package dev.nubstack.aegis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PunishmentCacheTest {

    private HikariDataSource dataSource;
    private ModerationService service;
    private List<PunishmentEvent> published;

    @BeforeEach
    void setUp() {
        dataSource = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:aegis-cache-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "aegis-test",
                2));
        JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource);
        repository.initSchema();
        published = new ArrayList<>();
        PunishmentBroadcaster broadcaster = new PunishmentBroadcaster() {
            @Override
            public void publish(PunishmentEvent event) {
                published.add(event);
            }

            @Override
            public void close() {
            }
        };
        service = new ModerationService(
                repository,
                new PunishmentCache(),
                broadcaster,
                new WebhookNotifier(null));
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void loadCache_rebuildsFromSqlAfterSimulatedRedisDrop() {
        UUID target = UUID.randomUUID();
        service.punish(
                PunishmentType.BAN,
                target,
                "Steve",
                UUID.randomUUID(),
                "Admin",
                "cheat",
                Duration.ofHours(1));
        assertTrue(service.activeBan(target).isPresent());

        // Simulate Redis drop: new service/cache, rebuild from SQL only.
        JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource);
        ModerationService rebuilt = new ModerationService(
                repository,
                new PunishmentCache(),
                new LocalPunishmentBroadcaster(),
                new WebhookNotifier(null));
        rebuilt.loadCache();
        assertTrue(rebuilt.activeBan(target).isPresent());
        assertTrue(published.size() >= 1);
    }
}
