package dev.nubstack.aegis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModerationServiceTest {

    private HikariDataSource dataSource;
    private ModerationService service;

    @BeforeEach
    void setUp() {
        dataSource = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:aegis-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "aegis-test",
                2));
        JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource);
        repository.initSchema();
        service = new ModerationService(
                repository,
                new PunishmentCache(),
                new LocalPunishmentBroadcaster(),
                new WebhookNotifier(null));
        service.loadCache();
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void ban_isActiveUntilRevoked() {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();

        service.punish(PunishmentType.BAN, target, "Steve", staff, "Admin", "cheating", Duration.ofHours(1));

        assertTrue(service.activeBan(target).isPresent());
        assertTrue(service.revoke(PunishmentType.BAN, target, staff));
        assertTrue(service.activeBan(target).isEmpty());
    }

    @Test
    void mute_blocksWhileActive() {
        UUID target = UUID.randomUUID();
        service.punish(
                PunishmentType.MUTE,
                target,
                "Alex",
                UUID.randomUUID(),
                "Mod",
                "spam",
                null);

        assertTrue(service.activeMute(target).isPresent());
        assertFalse(service.activeBan(target).isPresent());
    }

    @Test
    void warn_isStoredInHistory() {
        UUID target = UUID.randomUUID();
        service.punish(
                PunishmentType.WARN,
                target,
                "Alex",
                UUID.randomUUID(),
                "Mod",
                "language",
                null);

        assertEquals(1, service.history(target, 10).size());
        assertEquals(PunishmentType.WARN, service.history(target, 10).getFirst().type());
    }

    @Test
    void applyRemote_updatesCache() {
        UUID target = UUID.randomUUID();
        Punishment punishment = new Punishment(
                UUID.randomUUID(),
                target,
                "Remote",
                PunishmentType.BAN,
                "synced",
                UUID.randomUUID(),
                "OtherNode",
                java.time.Instant.now(),
                null,
                true);

        service.applyRemote(PunishmentEvent.created(punishment));

        assertTrue(service.activeBan(target).isPresent());
    }
}
