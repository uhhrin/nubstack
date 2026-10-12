package dev.nubstack.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerDataStoreTest {

    private HikariDataSource ds;
    private MutableClock clock;
    private PlayerDataStore store;

    @BeforeEach
    void setUp() {
        ds = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:loom-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "loom",
                2));
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        store = new PlayerDataStore(ds, clock);
        store.init();
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void tryAcquire_rejectsWhileOtherServerHoldsLease() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        assertFalse(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
        assertEquals("hub", store.find(id).orElseThrow().lockedBy());
    }

    @Test
    void tryAcquire_succeedsAfterLeaseExpires() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        clock.advance(Duration.ofSeconds(31));
        assertTrue(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
        assertEquals("minigame", store.find(id).orElseThrow().lockedBy());
    }

    @Test
    void saveAndRelease_bumpsVersionAndClearsLease() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        store.saveAndRelease(id, "hub", "blob-v1");
        PlayerDataStore.Snapshot snap = store.find(id).orElseThrow();
        assertEquals("blob-v1", snap.inventoryBlob());
        assertEquals(1L, snap.version());
        assertTrue(snap.lockedBy() == null || snap.lockedBy().isBlank());
        assertTrue(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
    }

    @Test
    void saveAndRelease_rejectsStaleWriterWithoutLease() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        store.saveAndRelease(id, "hub", "from-hub");
        assertTrue(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
        assertFalse(store.saveAndRelease(id, "hub", "stale-hub"));
        assertEquals("from-hub", store.find(id).orElseThrow().inventoryBlob());
        assertEquals(1L, store.find(id).orElseThrow().version());
    }

    @Test
    void forceUnlock_clearsLeaseForAdmin() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        store.forceUnlock(id, "admin");
        assertTrue(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
    }

    @Test
    void audit_recordsSaveLoadUnlock() {
        UUID id = UUID.randomUUID();
        store.audit("save", id, "hub", "bytes=12");
        store.audit("load", id, "minigame", "bytes=12");
        store.audit("unlock", id, "admin", "forced");
        List<PlayerDataStore.AuditEntry> rows = store.recentAudit(id, 10);
        assertEquals(3, rows.size());
        assertEquals("unlock", rows.get(0).eventType());
        assertEquals("load", rows.get(1).eventType());
        assertEquals("save", rows.get(2).eventType());
    }

    @Test
    void renew_extendsOwnLeaseAndRefusesOtherServers() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        clock.advance(Duration.ofSeconds(20));
        assertTrue(store.renew(id, "hub", Duration.ofSeconds(30)));
        clock.advance(Duration.ofSeconds(20));
        assertFalse(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
        assertFalse(store.renew(id, "minigame", Duration.ofSeconds(30)));
        assertFalse(store.renew(UUID.randomUUID(), "hub", Duration.ofSeconds(30)));
    }

    @Test
    void saveKeepingLease_bumpsVersionAndHoldsLease() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        assertTrue(store.saveKeepingLease(id, "hub", "snap", Duration.ofSeconds(30)));
        PlayerDataStore.Snapshot snap = store.find(id).orElseThrow();
        assertEquals(1L, snap.version());
        assertEquals("hub", snap.lockedBy());
        assertFalse(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
        assertFalse(store.saveKeepingLease(id, "minigame", "intruder", Duration.ofSeconds(30)));
        assertEquals("snap", store.find(id).orElseThrow().inventoryBlob());
    }

    @Test
    void saveAndRelease_writesAuditRowInSameTransaction() {
        UUID id = UUID.randomUUID();
        assertTrue(store.saveAndRelease(id, "hub", "abcd"));
        List<PlayerDataStore.AuditEntry> rows = store.recentAudit(id, 10);
        assertEquals(1, rows.size());
        assertEquals("save", rows.get(0).eventType());
        assertEquals("hub", rows.get(0).serverId());
        assertEquals("bytes=4", rows.get(0).detail());
        assertEquals(1L, store.find(id).orElseThrow().version());
    }

    @Test
    void forceUnlock_writesUnlockAuditRowWithActor() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        store.forceUnlock(id, "console");
        PlayerDataStore.AuditEntry row = store.recentAudit(id, 1).get(0);
        assertEquals("unlock", row.eventType());
        assertEquals("console", row.serverId());
        assertEquals("forced", row.detail());
    }

    @Test
    void release_clearsOnlyOwnLeaseAndKeepsBlob() {
        UUID id = UUID.randomUUID();
        assertTrue(store.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        assertTrue(store.saveKeepingLease(id, "hub", "keep-me", Duration.ofSeconds(30)));
        assertFalse(store.release(id, "minigame"));
        assertEquals("hub", store.find(id).orElseThrow().lockedBy());
        assertTrue(store.release(id, "hub"));
        PlayerDataStore.Snapshot snap = store.find(id).orElseThrow();
        assertEquals("keep-me", snap.inventoryBlob());
        assertEquals(1L, snap.version());
        assertTrue(store.tryAcquire(id, "minigame", Duration.ofSeconds(30)));
    }

    @Test
    void find_isEmptyForUnknownPlayer() {
        assertTrue(store.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void init_upgradesTableCreatedByFirstRelease() throws Exception {
        HikariDataSource old = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:loom-old-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "", "loom", 2));
        try (var c = old.getConnection(); var st = c.createStatement()) {
            st.execute("CREATE TABLE loom_player_data (player_id VARCHAR(36) PRIMARY KEY,"
                    + " inventory_blob LONGTEXT NOT NULL, updated_at TIMESTAMP NOT NULL)");
            st.execute("INSERT INTO loom_player_data VALUES ('" + UUID.randomUUID() + "', 'legacy', CURRENT_TIMESTAMP)");
        }
        PlayerDataStore upgraded = new PlayerDataStore(old, clock);
        upgraded.init();
        upgraded.init();
        UUID id = UUID.randomUUID();
        assertTrue(upgraded.tryAcquire(id, "hub", Duration.ofSeconds(30)));
        old.close();
    }

    @Test
    void tryAcquire_grantsExactlyOneOfManyConcurrentServers() throws Exception {
        UUID id = UUID.randomUUID();
        int servers = 6;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(servers);
        var start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
        for (int i = 0; i < servers; i++) {
            String server = "s" + i;
            results.add(pool.submit(() -> {
                start.await();
                return store.tryAcquire(id, server, Duration.ofSeconds(30));
            }));
        }
        start.countDown();
        int winners = 0;
        for (var result : results) {
            if (result.get(10, java.util.concurrent.TimeUnit.SECONDS)) {
                winners++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, winners);
    }
}
