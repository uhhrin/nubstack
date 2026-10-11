
package dev.nubstack.loom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferLockTest {
    @Test
    void rejectsConcurrentLock() {
        TransferLock lock = new TransferLock(Duration.ofSeconds(30));
        UUID id = UUID.randomUUID();
        assertTrue(lock.tryLock(id));
        assertFalse(lock.tryLock(id));
        lock.unlock(id);
        assertTrue(lock.tryLock(id));
    }
}
