
package dev.nubstack.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkEntityLimiterTest {
    @Test
    void enforcesCap() {
        ChunkEntityLimiter limiter = new ChunkEntityLimiter(5);
        assertTrue(limiter.allows(4));
        assertFalse(limiter.allows(5));
        assertEquals(3, limiter.overflow(8));
    }
}
