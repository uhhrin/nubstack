package dev.nubstack.aegis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PunishmentRepository {

    void initSchema();

    void insert(Punishment punishment);

    boolean deactivate(UUID targetId, PunishmentType type, UUID staffId);

    boolean deactivateById(UUID punishmentId, UUID staffId);

    Optional<Punishment> findById(UUID punishmentId);

    Optional<Punishment> findActive(UUID targetId, PunishmentType type);

    List<Punishment> history(UUID targetId, int limit, int offset);

    List<Punishment> findActiveForTarget(UUID targetId);

    List<Punishment> findAllActive();
}
