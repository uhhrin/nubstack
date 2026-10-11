package dev.nubstack.aegis;

import java.util.UUID;

public record PunishmentEvent(
        String action,
        Punishment punishment,
        UUID targetId,
        PunishmentType type) {

    public static PunishmentEvent created(Punishment punishment) {
        return new PunishmentEvent("create", punishment, punishment.targetId(), punishment.type());
    }

    public static PunishmentEvent revoked(UUID targetId, PunishmentType type, Punishment snapshot) {
        return new PunishmentEvent("revoke", snapshot, targetId, type);
    }
}
