package dev.nubstack.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CullStatsTest {

    @Test
    void countersAccumulate() {
        CullStats stats = new CullStats();
        stats.spawnCancelled();
        stats.spawnCancelled();
        stats.culled(3);
        stats.culled(0);
        assertEquals(2, stats.cancelledSpawns());
        assertEquals(3, stats.culledEntities());
    }
}
