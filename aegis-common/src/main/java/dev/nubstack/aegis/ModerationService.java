package dev.nubstack.aegis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class ModerationService {

    public static final int HISTORY_PAGE_SIZE = 8;

    private final PunishmentRepository repository;
    private final PunishmentCache cache;
    private final PunishmentBroadcaster broadcaster;
    private final AtomicReference<WebhookNotifier> webhookNotifier;

    public ModerationService(
            PunishmentRepository repository,
            PunishmentCache cache,
            PunishmentBroadcaster broadcaster,
            WebhookNotifier webhookNotifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.webhookNotifier = new AtomicReference<>(Objects.requireNonNull(webhookNotifier, "webhookNotifier"));
    }

    public void replaceWebhook(WebhookNotifier next) {
        webhookNotifier.set(Objects.requireNonNull(next, "next"));
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
        webhookNotifier.get().notifyPunishment(punishment);
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

    public boolean rollback(UUID punishmentId, UUID staffId) {
        Objects.requireNonNull(punishmentId, "punishmentId");
        Objects.requireNonNull(staffId, "staffId");
        Optional<Punishment> existing = repository.findById(punishmentId);
        if (existing.isEmpty()) {
            return false;
        }
        Punishment row = existing.get();
        if (row.type() == PunishmentType.WARN) {
            throw new IllegalArgumentException("WARN rows cannot be rolled back");
        }
        boolean changed = repository.deactivateById(punishmentId, staffId);
        if (changed) {
            cache.remove(row.targetId(), row.type());
            broadcaster.publish(PunishmentEvent.revoked(row.targetId(), row.type(), row));
        }
        return changed;
    }

    public Optional<Punishment> findById(UUID punishmentId) {
        return repository.findById(punishmentId);
    }

    public Optional<Punishment> activeBan(UUID targetId) {
        return cache.get(targetId, PunishmentType.BAN)
                .or(() -> refreshActive(targetId, PunishmentType.BAN));
    }

    public Optional<Punishment> activeMute(UUID targetId) {
        return cache.get(targetId, PunishmentType.MUTE)
                .or(() -> refreshActive(targetId, PunishmentType.MUTE));
    }

    public List<Punishment> check(UUID targetId) {
        Instant now = Instant.now();
        return repository.findActiveForTarget(targetId).stream()
                .filter(row -> row.isEffective(now))
                .toList();
    }

    /** Active punishments for a target (same as {@link #check}). */
    public List<Punishment> activeFor(UUID targetId) {
        return check(targetId);
    }

    public List<Punishment> history(UUID targetId, int limit) {
        return repository.history(targetId, limit, 0);
    }

    public List<Punishment> historyPage(UUID targetId, int page) {
        return historyPage(targetId, page, HISTORY_PAGE_SIZE);
    }

    public List<Punishment> historyPage(UUID targetId, int page, int pageSize) {
        int size = pageSize < 1 ? HISTORY_PAGE_SIZE : pageSize;
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * size;
        return repository.history(targetId, size, offset);
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
        // shortcut: after a Redis drop, cache may miss until the next local check hits SQL
        Optional<Punishment> found = repository.findActive(targetId, type)
                .filter(punishment -> punishment.isEffective(Instant.now()));
        found.ifPresent(cache::put);
        return found;
    }
}
