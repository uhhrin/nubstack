package dev.nubstack.aegis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffCooldownTest {

    @Test
    void blocksSecondAcquireWithinWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        StaffCooldown cooldown = new StaffCooldown(Duration.ofSeconds(2), clock);
        UUID staff = UUID.randomUUID();

        assertTrue(cooldown.tryAcquire(staff));
        assertFalse(cooldown.tryAcquire(staff));
        assertEquals(Duration.ofSeconds(2), cooldown.remaining(staff));

        clock.advance(Duration.ofSeconds(2));
        assertTrue(cooldown.tryAcquire(staff));
    }

    @Test
    void zeroCooldownAlwaysAllows() {
        StaffCooldown cooldown = new StaffCooldown(Duration.ZERO);
        UUID staff = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(staff));
        assertTrue(cooldown.tryAcquire(staff));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
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
