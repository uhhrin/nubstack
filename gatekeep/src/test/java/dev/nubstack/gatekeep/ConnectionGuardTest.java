package dev.nubstack.gatekeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectionGuardTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final TestClock clock = new TestClock();

    private ConnectionGuard guard(int max, boolean group64, List<Cidr> exempt) {
        return new ConnectionGuard(new ConnectionGuard.Settings(max, WINDOW, group64, exempt), clock);
    }

    @Test
    void blocksBurst() {
        ConnectionGuard guard = new ConnectionGuard(3, Duration.ofSeconds(60));
        assertTrue(guard.allow("1.2.3.4"));
        assertTrue(guard.allow("1.2.3.4"));
        assertTrue(guard.allow("1.2.3.4"));
        assertFalse(guard.allow("1.2.3.4"));
        assertTrue(guard.allow("9.9.9.9"));
    }

    @Test
    void allowsAgainAfterWindowPasses() {
        ConnectionGuard guard = guard(2, true, List.of());
        assertTrue(guard.allow("1.2.3.4"));
        assertTrue(guard.allow("1.2.3.4"));
        assertFalse(guard.allow("1.2.3.4"));
        clock.advance(WINDOW.plusSeconds(1));
        assertTrue(guard.allow("1.2.3.4"));
    }

    @Test
    void denyDecisionReportsAttemptsWindowAndRetry() {
        ConnectionGuard guard = guard(2, true, List.of());
        guard.check("1.2.3.4");
        clock.advance(Duration.ofSeconds(10));
        guard.check("1.2.3.4");
        clock.advance(Duration.ofSeconds(5));
        ConnectionGuard.Decision denied = guard.check("1.2.3.4");
        assertFalse(denied.allowed());
        assertEquals(2, denied.attempts());
        assertEquals(WINDOW, denied.window());
        assertEquals(Duration.ofSeconds(45), denied.retryAfter());
        assertTrue(denied.supportCode().matches("GK-[0-9A-F]{4}"));
    }

    @Test
    void supportCodeIsStablePerAddressAndDiffersAcrossAddresses() {
        ConnectionGuard guard = guard(1, true, List.of());
        guard.check("1.2.3.4");
        String first = guard.check("1.2.3.4").supportCode();
        assertEquals(first, guard.check("1.2.3.4").supportCode());
        guard.check("5.6.7.8");
        assertFalse(first.equals(guard.check("5.6.7.8").supportCode()));
    }

    @Test
    void staleAddressesAreEvictedSoMapCannotGrowForever() {
        ConnectionGuard guard = guard(5, true, List.of());
        for (int i = 1; i <= 50; i++) {
            guard.allow("10.1.0." + i);
        }
        assertEquals(50, guard.trackedCount());
        clock.advance(WINDOW.multipliedBy(12));
        guard.allow("8.8.8.8");
        assertEquals(1, guard.trackedCount());
    }

    @Test
    void exemptAddressesAreNeverThrottledOrTracked() {
        ConnectionGuard guard = guard(1, true, List.of(Cidr.parse("127.0.0.0/8"), Cidr.parse("::1/128")));
        for (int i = 0; i < 10; i++) {
            assertTrue(guard.allow("127.0.0.1"));
            assertTrue(guard.allow("::1"));
        }
        assertEquals(0, guard.trackedCount());
        assertTrue(guard.allow("1.2.3.4"));
        assertFalse(guard.allow("1.2.3.4"));
    }

    @Test
    void ipv6AddressesInSameSlash64ShareOneBucket() {
        ConnectionGuard guard = guard(2, true, List.of());
        assertTrue(guard.allow("2001:db8:0:1::1"));
        assertTrue(guard.allow("2001:db8:0:1:aaaa:bbbb:cccc:dddd"));
        assertFalse(guard.allow("2001:db8:0:1::99"));
        assertTrue(guard.allow("2001:db8:0:2::1"));
        assertEquals(2, guard.trackedCount());
    }

    @Test
    void ipv6GroupingCanBeDisabled() {
        ConnectionGuard guard = guard(1, false, List.of());
        assertTrue(guard.allow("2001:db8:0:1::1"));
        assertTrue(guard.allow("2001:db8:0:1::2"));
        assertFalse(guard.allow("2001:db8:0:1::1"));
    }

    @Test
    void clearForgetsOneAddressAndClearAllForgetsEverything() {
        ConnectionGuard guard = guard(1, true, List.of());
        guard.allow("1.2.3.4");
        guard.allow("5.6.7.8");
        assertFalse(guard.allow("1.2.3.4"));
        assertTrue(guard.clear("1.2.3.4"));
        assertFalse(guard.clear("1.2.3.4"));
        assertTrue(guard.allow("1.2.3.4"));
        assertEquals(2, guard.clearAll());
        assertEquals(0, guard.trackedCount());
    }

    @Test
    void inspectShowsAttemptsWithoutCountingAsAnAttempt() {
        ConnectionGuard guard = guard(2, true, List.of(Cidr.parse("127.0.0.1")));
        assertTrue(guard.inspect("1.2.3.4").isEmpty());
        guard.allow("1.2.3.4");
        ConnectionGuard.Entry entry = guard.inspect("1.2.3.4").orElseThrow();
        assertEquals(1, entry.attempts());
        assertEquals(1, guard.inspect("1.2.3.4").orElseThrow().attempts());
        assertTrue(guard.isExempt("127.0.0.1"));
        assertFalse(guard.isExempt("1.2.3.4"));
    }

    @Test
    void snapshotsAreSortedByAttemptsAndLimited() {
        ConnectionGuard guard = guard(9, true, List.of());
        guard.allow("1.1.1.1");
        guard.allow("2.2.2.2");
        guard.allow("2.2.2.2");
        guard.allow("3.3.3.3");
        guard.allow("3.3.3.3");
        guard.allow("3.3.3.3");
        List<ConnectionGuard.Entry> top = guard.snapshots(2);
        assertEquals(2, top.size());
        assertEquals("3.3.3.3", top.get(0).key());
        assertEquals("2.2.2.2", top.get(1).key());
    }

    @Test
    void deniedCounterTracksRefusals() {
        ConnectionGuard guard = guard(1, true, List.of());
        guard.allow("1.2.3.4");
        guard.allow("1.2.3.4");
        guard.allow("1.2.3.4");
        assertEquals(2, guard.deniedTotal());
    }

    @Test
    void denyLogLineIsStructured() {
        ConnectionGuard guard = guard(1, true, List.of());
        guard.check("1.2.3.4");
        ConnectionGuard.Decision denied = guard.check("1.2.3.4");
        assertEquals("event=gatekeep.deny ip=1.2.3.4 attempts=1 window=60s", denied.logLine("1.2.3.4"));
    }

    @Test
    void settingsCanBeReplacedWithoutDroppingCounters() {
        ConnectionGuard guard = guard(1, true, List.of());
        guard.allow("1.2.3.4");
        assertFalse(guard.allow("1.2.3.4"));
        guard.configure(new ConnectionGuard.Settings(5, WINDOW, true, List.of()));
        assertTrue(guard.allow("1.2.3.4"));
    }
}
