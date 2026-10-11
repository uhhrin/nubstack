package dev.nubstack.aegis;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Punishment(
        UUID id,
        UUID targetId,
        String targetName,
        PunishmentType type,
        String reason,
        UUID staffId,
        String staffName,
        Instant createdAt,
        Instant expiresAt,
        boolean active) {

    public Punishment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(staffName, "staffName");
        Objects.requireNonNull(createdAt, "createdAt");
        if (targetName.isBlank()) {
            throw new IllegalArgumentException("targetName must not be blank");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (staffName.isBlank()) {
            throw new IllegalArgumentException("staffName must not be blank");
        }
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isEffective(Instant now) {
        return active && !isExpired(now);
    }
}
