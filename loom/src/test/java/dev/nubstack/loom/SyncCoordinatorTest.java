package dev.nubstack.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyncCoordinatorTest {

    private PlayerDataStore store;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:loom-sync-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        store = new PlayerDataStore(ds, clock);
        store.init();
    }

    @Test
    void join_waitsForQuitSaveOnOtherServer_thenLoadsNewestBlob() {
        UUID player = UUID.randomUUID();
        SyncCoordinator hub = coordinator("hub", SyncCoordinator.FailurePolicy.KICK);
        SyncCoordinator minigame = coordinator("minigame", SyncCoordinator.FailurePolicy.KICK);

        // The player is online on hub, so hub holds the session lease.
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));

        // The player switches: minigame joins before hub's async quit save lands.
        Instant joinStart = clock.instant();
        assertEquals(SyncCoordinator.JoinDecision.WAIT, minigame.pollJoin(player, joinStart));
        clock.advance(Duration.ofSeconds(1));
        assertEquals(SyncCoordinator.JoinDecision.WAIT, minigame.pollJoin(player, joinStart));

        assertTrue(hub.saveAndRelease(player, "from-hub"));
        assertEquals(SyncCoordinator.JoinDecision.READY, minigame.pollJoin(player, joinStart));
        assertEquals("from-hub", minigame.loadBlob(player).orElseThrow());
        assertEquals(1L, store.find(player).orElseThrow().version());
    }

    @Test
    void join_timesOutToKick() {
        UUID player = UUID.randomUUID();
        SyncCoordinator hub = coordinator("hub", SyncCoordinator.FailurePolicy.KICK);
        SyncCoordinator minigame = coordinator("minigame", SyncCoordinator.FailurePolicy.KICK);
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));

        Instant joinStart = clock.instant();
        assertEquals(SyncCoordinator.JoinDecision.WAIT, minigame.pollJoin(player, joinStart));
        clock.advance(Duration.ofSeconds(3));
        assertEquals(SyncCoordinator.JoinDecision.FAILED_KICK, minigame.pollJoin(player, joinStart));
    }

    @Test
    void join_timeoutHonoursJoinEmptyAndKeepLocalPolicies() {
        UUID player = UUID.randomUUID();
        SyncCoordinator hub = coordinator("hub", SyncCoordinator.FailurePolicy.KICK);
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));
        Instant joinStart = clock.instant();
        clock.advance(Duration.ofSeconds(3));

        assertEquals(SyncCoordinator.JoinDecision.FAILED_JOIN_EMPTY,
                coordinator("a", SyncCoordinator.FailurePolicy.JOIN_EMPTY).pollJoin(player, joinStart));
        assertEquals(SyncCoordinator.JoinDecision.FAILED_KEEP_LOCAL,
                coordinator("b", SyncCoordinator.FailurePolicy.KEEP_LOCAL).pollJoin(player, joinStart));
    }

    @Test
    void join_succeedsAtTimeoutWhenLeaseIsFreeByThen() {
        UUID player = UUID.randomUUID();
        SyncCoordinator hub = coordinator("hub", SyncCoordinator.FailurePolicy.KICK);
        SyncCoordinator minigame = coordinator("minigame", SyncCoordinator.FailurePolicy.KICK);
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));
        Instant joinStart = clock.instant();
        clock.advance(Duration.ofSeconds(4));
        hub.saveAndRelease(player, "late");
        assertEquals(SyncCoordinator.JoinDecision.READY, minigame.pollJoin(player, joinStart));
    }

    @Test
    void crashedServerLease_expiresAndOtherServerProceeds() {
        UUID player = UUID.randomUUID();
        SyncCoordinator hub = coordinator("hub", SyncCoordinator.FailurePolicy.KICK);
        SyncCoordinator minigame = coordinator("minigame", SyncCoordinator.FailurePolicy.KICK);
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));
        clock.advance(Duration.ofSeconds(31));
        assertEquals(SyncCoordinator.JoinDecision.READY, minigame.pollJoin(player, clock.instant()));
        assertFalse(hub.saveAndRelease(player, "stale-from-crashed-hub"));
    }

    @Test
    void renew_keepsSessionLeaseAcrossTtl() {
        UUID player = UUID.randomUUID();
        SyncCoordinator hub = coordinator("hub", SyncCoordinator.FailurePolicy.KICK);
        SyncCoordinator minigame = coordinator("minigame", SyncCoordinator.FailurePolicy.KICK);
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofSeconds(20));
            assertTrue(hub.renew(player));
        }
        assertEquals(SyncCoordinator.JoinDecision.WAIT, minigame.pollJoin(player, clock.instant()));
    }

    @Test
    void loadBlob_isEmptyForFirstJoinAndAuditedOtherwise() {
        UUID player = UUID.randomUUID();
        SyncCoordinator hub = coordinator("hub", SyncCoordinator.FailurePolicy.KICK);
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));
        assertTrue(hub.loadBlob(player).isEmpty());
        assertTrue(store.recentAudit(player, 10).isEmpty());

        hub.saveAndRelease(player, "data");
        assertEquals(SyncCoordinator.JoinDecision.READY, hub.pollJoin(player, clock.instant()));
        assertEquals("data", hub.loadBlob(player).orElseThrow());
        assertEquals("load", store.recentAudit(player, 10).get(0).eventType());
    }

    @Test
    void blocked_waitsThenAppliesPolicyWhenDatabaseIsDown() {
        SyncCoordinator minigame = coordinator("minigame", SyncCoordinator.FailurePolicy.JOIN_EMPTY);
        Instant joinStart = clock.instant();
        assertEquals(SyncCoordinator.JoinDecision.WAIT, minigame.blocked(joinStart));
        clock.advance(Duration.ofSeconds(3));
        assertEquals(SyncCoordinator.JoinDecision.FAILED_JOIN_EMPTY, minigame.blocked(joinStart));
    }

    @Test
    void failurePolicy_parsesNamesAndRejectsUnknown() {
        assertEquals(SyncCoordinator.FailurePolicy.KICK, SyncCoordinator.FailurePolicy.parse(null));
        assertEquals(SyncCoordinator.FailurePolicy.JOIN_EMPTY, SyncCoordinator.FailurePolicy.parse("join-empty"));
        assertEquals(SyncCoordinator.FailurePolicy.KEEP_LOCAL, SyncCoordinator.FailurePolicy.parse("KEEP-LOCAL"));
        assertThrows(IllegalArgumentException.class, () -> SyncCoordinator.FailurePolicy.parse("explode"));
    }

    private SyncCoordinator coordinator(String serverId, SyncCoordinator.FailurePolicy policy) {
        return new SyncCoordinator(store, clock, serverId, Duration.ofSeconds(30), Duration.ofSeconds(3), policy);
    }
}
