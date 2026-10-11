package dev.nubstack.aegis;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory view of active bans and mutes for fast enforcement checks.
 */
public final class PunishmentCache {

    private final Map<UUID, Punishment> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    public void put(Punishment punishment) {
        if (punishment.type() == PunishmentType.WARN) {
            return;
        }
        Map<UUID, Punishment> target = mapFor(punishment.type());
        if (punishment.isEffective(Instant.now())) {
            target.put(punishment.targetId(), punishment);
        } else {
            target.remove(punishment.targetId());
        }
    }

    public void remove(UUID targetId, PunishmentType type) {
        if (type == PunishmentType.WARN) {
            return;
        }
        mapFor(type).remove(targetId);
    }

    public Optional<Punishment> get(UUID targetId, PunishmentType type) {
        if (type == PunishmentType.WARN) {
            return Optional.empty();
        }
        Punishment punishment = mapFor(type).get(targetId);
        if (punishment == null) {
            return Optional.empty();
        }
        if (!punishment.isEffective(Instant.now())) {
            mapFor(type).remove(targetId, punishment);
            return Optional.empty();
        }
        return Optional.of(punishment);
    }

    public void replaceAll(Iterable<Punishment> punishments) {
        bans.clear();
        mutes.clear();
        for (Punishment punishment : punishments) {
            put(punishment);
        }
    }

    private Map<UUID, Punishment> mapFor(PunishmentType type) {
        return switch (type) {
            case BAN -> bans;
            case MUTE -> mutes;
            case WARN -> throw new IllegalArgumentException("WARN is not cached");
        };
    }
}
