package dev.nubstack.aegis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ModerationService {

    private final PunishmentRepository repository;
    private final PunishmentCache cache;
    private final PunishmentBroadcaster broadcaster;
    private final WebhookNotifier webhookNotifier;

    public ModerationService(
            PunishmentRepository repository,
            PunishmentCache cache,
            PunishmentBroadcaster broadcaster,
            WebhookNotifier webhookNotifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.webhookNotifier = Objects.requireNonNull(webhookNotifier, "webhookNotifier");
    }

    public void loadCache() {
        cache.replaceAll(repository.findAllActive());
    }

    public Punishment punish(
            PunishmentType type,
            UUID targetId,
            String targetName,
            UUID staffId,
            String staffName,
            String reason,
            Duration duration) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(staffName, "staffName");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }

        if (type == PunishmentType.BAN || type == PunishmentType.MUTE) {
            repository.deactivate(targetId, type, staffId);
            cache.remove(targetId, type);
        }

        Instant createdAt = Instant.now();
        Instant expiresAt = duration == null ? null : createdAt.plus(duration);
        Punishment punishment = new Punishment(
                UUID.randomUUID(),
                targetId,
                targetName,
                type,
                reason.trim(),
                staffId,
                staffName,
                createdAt,
                expiresAt,
                true);

        repository.insert(punishment);
        cache.put(punishment);
        PunishmentEvent event = PunishmentEvent.created(punishment);
        broadcaster.publish(event);
        webhookNotifier.notifyPunishment(punishment);
        return punishment;
    }

    public boolean revoke(PunishmentType type, UUID targetId, UUID staffId) {
        if (type == PunishmentType.WARN) {
            throw new IllegalArgumentException("WARN cannot be revoked; issue a new history entry instead");
        }
        boolean changed = repository.deactivate(targetId, type, staffId);
        if (changed) {
            cache.remove(targetId, type);
            broadcaster.publish(PunishmentEvent.revoked(targetId, type, null));
        }
        return changed;
    }

    public Optional<Punishment> activeBan(UUID targetId) {
        return cache.get(targetId, PunishmentType.BAN)
                .or(() -> refreshActive(targetId, PunishmentType.BAN));
    }

    public Optional<Punishment> activeMute(UUID targetId) {
        return cache.get(targetId, PunishmentType.MUTE)
                .or(() -> refreshActive(targetId, PunishmentType.MUTE));
    }

    public List<Punishment> history(UUID targetId, int limit) {
        return repository.history(targetId, limit);
    }

    public void applyRemote(PunishmentEvent event) {
        if ("create".equals(event.action()) && event.punishment() != null) {
            cache.put(event.punishment());
            return;
        }
        if ("revoke".equals(event.action())) {
            cache.remove(event.targetId(), event.type());
        }
    }

    private Optional<Punishment> refreshActive(UUID targetId, PunishmentType type) {
        Optional<Punishment> found = repository.findActive(targetId, type)
                .filter(punishment -> punishment.isEffective(Instant.now()));
        found.ifPresent(cache::put);
        return found;
    }
}
