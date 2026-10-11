package dev.nubstack.aegis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PunishmentRepository {

    void initSchema();

    void insert(Punishment punishment);

    boolean deactivate(UUID targetId, PunishmentType type, UUID staffId);

    Optional<Punishment> findActive(UUID targetId, PunishmentType type);

    List<Punishment> history(UUID targetId, int limit);

    List<Punishment> findAllActive();
}
