package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkServiceTest {
    private HikariDataSource ds;
    private MutableClock clock;
    private LinkService links;

    @BeforeEach
    void setUp() {
        ds = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:tether-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "", "tether", 2));
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        links = new LinkService(ds, clock, 8, 300, 3);
        links.init();
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void linksViaCode() {
        UUID player = UUID.randomUUID();
        String code = links.createCode(player);
        assertEquals(8, code.length());
        LinkService.RedeemResult redeemed = links.redeem(code);
        assertTrue(redeemed.success());
        assertEquals(player, redeemed.playerId());
        links.link(player, "1234567890", "nub");
        assertTrue(links.isLinked(player));
        assertEquals("1234567890", links.discordId(player).orElseThrow());
        assertEquals("nub", links.linkByPlayer(player).orElseThrow().discordTag());
    }

    @Test
    void createCode_oneActivePerPlayer() {
        UUID player = UUID.randomUUID();
        String first = links.createCode(player);
        String second = links.createCode(player);
        assertNotEquals(first, second);
        assertEquals(LinkService.RedeemStatus.UNKNOWN, links.redeem(first).status());
        assertEquals(LinkService.RedeemStatus.OK, links.redeem(second).status());
    }

    @Test
    void redeem_enforcesTtl() {
        UUID player = UUID.randomUUID();
        String code = links.createCode(player);
        clock.advanceSeconds(301);
        assertEquals(LinkService.RedeemStatus.EXPIRED, links.redeem(code).status());
        assertFalse(links.isLinked(player));
        assertEquals(0, links.pendingCodeCount());
    }

    @Test
    void purgeExpired_removesStaleCodes() {
        UUID player = UUID.randomUUID();
        links.createCode(player);
        assertEquals(1, links.pendingCodeCount());
        clock.advanceSeconds(301);
        assertEquals(1, links.purgeExpired());
        assertEquals(0, links.pendingCodeCount());
    }

    @Test
    void noteFailedAttempt_locksAfterMaxAttempts() {
        UUID player = UUID.randomUUID();
        String code = links.createCode(player);
        assertEquals(LinkService.RedeemStatus.UNKNOWN, links.noteFailedAttempt(code).status());
        assertEquals(LinkService.RedeemStatus.UNKNOWN, links.noteFailedAttempt(code).status());
        assertEquals(LinkService.RedeemStatus.LOCKED, links.noteFailedAttempt(code).status());
        assertEquals(LinkService.RedeemStatus.UNKNOWN, links.redeem(code).status());
        assertFalse(links.isLinked(player));
    }

    @Test
    void cancelCode_andUnlink() {
        UUID player = UUID.randomUUID();
        links.createCode(player);
        assertTrue(links.cancelCode(player));
        assertTrue(links.pendingCode(player).isEmpty());
        links.link(player, "55", "tag");
        assertTrue(links.unlink(player));
        assertFalse(links.isLinked(player));
    }

    @Test
    void codes_areNotTriviallySequential() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            codes.add(links.createCode(UUID.randomUUID()));
        }
        assertEquals(20, codes.size());
    }

    @Test
    void linkByDiscordId_andAdminCounts() {
        UUID player = UUID.randomUUID();
        links.link(player, "99", "mod");
        assertEquals(player, links.linkByDiscordId("99").orElseThrow().playerId());
        links.createCode(UUID.randomUUID());
        assertEquals(1, links.pendingCodeCount());
        assertEquals(1, links.listPendingCodes(10).size());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
