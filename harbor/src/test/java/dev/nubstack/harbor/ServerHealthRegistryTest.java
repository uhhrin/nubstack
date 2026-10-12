package dev.nubstack.harbor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ServerHealthRegistryTest {

    @Test
    void hysteresisAvoidsFlapping() {
        ServerHealthRegistry health = new ServerHealthRegistry(2, 2);
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        health.probe("hub", true, 1, 20, t0);
        assertTrue(health.isAvailable("hub"));

        health.probe("hub", false, 0, 0, t0.plusSeconds(1));
        assertTrue(health.isAvailable("hub"));

        health.probe("hub", false, 0, 0, t0.plusSeconds(2));
        assertFalse(health.isAvailable("hub"));
        assertEquals(t0.plusSeconds(2), health.get("hub").checkedAt());

        health.probe("hub", true, 1, 20, t0.plusSeconds(3));
        assertFalse(health.isAvailable("hub"));

        health.probe("hub", true, 1, 20, t0.plusSeconds(4));
        assertTrue(health.isAvailable("hub"));
    }

    @Test
    void fullServerIsUnavailable() {
        ServerHealthRegistry health = new ServerHealthRegistry(1, 1);
        health.probe("hub", true, 20, 20, Instant.EPOCH);
        assertFalse(health.isAvailable("hub"));
    }
}
