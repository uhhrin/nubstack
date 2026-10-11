
package dev.nubstack.harbor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueueManagerTest {

    @Test
    void higherPriorityDequeuesFirst() {
        QueueManager manager = new QueueManager();
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        manager.enqueue("survival", manager.entry(low, "Low", 1));
        manager.enqueue("survival", manager.entry(high, "High", 10));
        assertEquals(high, manager.poll("survival").orElseThrow().playerId());
        assertEquals(low, manager.poll("survival").orElseThrow().playerId());
        assertTrue(manager.poll("survival").isEmpty());
    }
}
