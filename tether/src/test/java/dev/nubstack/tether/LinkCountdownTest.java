package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LinkCountdownTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void tick_reportsRemainingSecondsThenExpiresOnce() {
        MutableClock clock = new MutableClock();
        LinkCountdown countdown = new LinkCountdown(clock);
        UUID id = UUID.randomUUID();
        countdown.track(id, 10);

        List<Long> ticks = new ArrayList<>();
        List<UUID> expired = new ArrayList<>();
        countdown.tick((player, left) -> ticks.add(left), expired::add);
        clock.advance(4);
        countdown.tick((player, left) -> ticks.add(left), expired::add);
        clock.advance(6);
        countdown.tick((player, left) -> ticks.add(left), expired::add);
        countdown.tick((player, left) -> ticks.add(left), expired::add);

        assertEquals(List.of(10L, 6L), ticks);
        assertEquals(List.of(id), expired);
        assertFalse(countdown.isTracking(id));
    }

    @Test
    void stop_removesPlayerWithoutExpiry() {
        LinkCountdown countdown = new LinkCountdown(new MutableClock());
        UUID id = UUID.randomUUID();
        countdown.track(id, 30);
        assertTrue(countdown.isTracking(id));
        countdown.stop(id);

        List<UUID> expired = new ArrayList<>();
        countdown.tick((player, left) -> { }, expired::add);
        assertTrue(expired.isEmpty());
        assertFalse(countdown.isTracking(id));
    }

    @Test
    void track_replacesExistingDeadline() {
        MutableClock clock = new MutableClock();
        LinkCountdown countdown = new LinkCountdown(clock);
        UUID id = UUID.randomUUID();
        countdown.track(id, 5);
        countdown.track(id, 50);
        List<Long> ticks = new ArrayList<>();
        countdown.tick((player, left) -> ticks.add(left), player -> { });
        assertEquals(List.of(50L), ticks);
    }
}
