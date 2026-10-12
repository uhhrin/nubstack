package dev.nubstack.aegis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcPunishmentRepositoryTest {

    private HikariDataSource dataSource;
    private JdbcPunishmentRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:aegis-jdbc-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "aegis-test",
                2));
        repository = new JdbcPunishmentRepository(dataSource);
        repository.initSchema();
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void initSchema_isIdempotentAndFindByIdWorks() {
        repository.initSchema();
        UUID id = UUID.randomUUID();
        Punishment row = new Punishment(
                id,
                UUID.randomUUID(),
                "Steve",
                PunishmentType.BAN,
                "cheating",
                UUID.randomUUID(),
                "Admin",
                Instant.now(),
                null,
                true);
        repository.insert(row);
        assertEquals(id, repository.findById(id).orElseThrow().id());
        assertTrue(repository.deactivateById(id, UUID.randomUUID()));
        assertTrue(repository.findById(id).orElseThrow().active() == false);
    }
}
