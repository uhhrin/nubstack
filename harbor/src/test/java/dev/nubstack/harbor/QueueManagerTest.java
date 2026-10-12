package dev.nubstack.harbor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueueManagerTest {

    @Test
    void higherPriorityDequeuesFirst() {
        QueueManager manager = new QueueManager();
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        manager.enqueue("survival", entry(low, "Low", 1, Instant.parse("2026-01-01T00:00:00Z")));
        manager.enqueue("survival", entry(high, "High", 10, Instant.parse("2026-01-01T00:00:01Z")));
        assertEquals(high, manager.poll("survival").orElseThrow().playerId());
        assertEquals(low, manager.poll("survival").orElseThrow().playerId());
        assertTrue(manager.poll("survival").isEmpty());
    }

    @Test
    void fifoWithinSamePriority() {
        QueueManager manager = new QueueManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        manager.enqueue("hub", entry(first, "A", 5, Instant.parse("2026-01-01T00:00:00Z")));
        manager.enqueue("hub", entry(second, "B", 5, Instant.parse("2026-01-01T00:00:01Z")));
        assertEquals(first, manager.poll("hub").orElseThrow().playerId());
        assertEquals(second, manager.poll("hub").orElseThrow().playerId());
    }

    @Test
    void reenqueueDoesNotDuplicate() {
        QueueManager manager = new QueueManager();
        UUID id = UUID.randomUUID();
        manager.enqueue("hub", entry(id, "A", 1, Instant.parse("2026-01-01T00:00:00Z")));
        manager.enqueue("hub", entry(id, "A", 10, Instant.parse("2026-01-01T00:00:02Z")));
        assertEquals(1, manager.size("hub"));
        assertEquals(10, manager.poll("hub").orElseThrow().priority());
    }

    @Test
    void leaveRemovesFromAllQueues() {
        QueueManager manager = new QueueManager();
        UUID id = UUID.randomUUID();
        manager.enqueue("a", entry(id, "A", 1, Instant.EPOCH));
        manager.enqueue("b", entry(id, "A", 1, Instant.EPOCH));
        assertTrue(manager.leave(id));
        assertEquals(0, manager.size("a"));
        assertEquals(0, manager.size("b"));
        assertFalse(manager.leave(id));
    }

    @Test
    void tryEnqueueRespectsMaxSize() {
        QueueManager manager = new QueueManager();
        manager.setMaxQueueSize(1);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(QueueManager.EnqueueResult.QUEUED,
                manager.enqueue("hub", entry(a, "A", 1, Instant.EPOCH)));
        assertEquals(QueueManager.EnqueueResult.FULL,
                manager.enqueue("hub", entry(b, "B", 1, Instant.EPOCH)));
        assertEquals(QueueManager.EnqueueResult.QUEUED,
                manager.enqueue("hub", entry(a, "A", 5, Instant.EPOCH)));
    }

    @Test
    void clearEmptiesServerQueue() {
        QueueManager manager = new QueueManager();
        manager.enqueue("hub", entry(UUID.randomUUID(), "A", 1, Instant.EPOCH));
        assertEquals(1, manager.clear("hub").size());
        assertEquals(0, manager.size("hub"));
    }

    @Test
    void peekDoesNotRemoveAndPriorityOfReportsEntry() {
        QueueManager manager = new QueueManager();
        UUID id = UUID.randomUUID();
        manager.enqueue("hub", entry(id, "A", 10, Instant.EPOCH));
        assertEquals(id, manager.peek("hub").orElseThrow().playerId());
        assertEquals(1, manager.size("hub"));
        assertEquals(10, manager.priorityOf("hub", id).orElseThrow());
        assertTrue(manager.priorityOf("hub", UUID.randomUUID()).isEmpty());
        assertTrue(manager.peek("none").isEmpty());
    }

    @Test
    void serverOfAndQueuedServersTrackMembership() {
        QueueManager manager = new QueueManager();
        UUID id = UUID.randomUUID();
        assertTrue(manager.serverOf(id).isEmpty());
        manager.enqueue("hub", entry(id, "A", 1, Instant.EPOCH));
        assertEquals("hub", manager.serverOf(id).orElseThrow());
        assertTrue(manager.queuedServers().contains("hub"));
        manager.leave(id);
        assertFalse(manager.queuedServers().contains("hub"));
    }

    @Test
    void pauseAndResumeToggleFlag() {
        QueueManager manager = new QueueManager();
        assertFalse(manager.isPaused("hub"));
        manager.pause("hub");
        assertTrue(manager.isPaused("hub"));
        manager.resume("hub");
        assertFalse(manager.isPaused("hub"));
    }

    @Test
    void etaIsUnknownUntilEnoughSendsThenUsesRollingRate() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        QueueManager manager = new QueueManager(clock);
        assertTrue(manager.etaSeconds(4).isEmpty());
        manager.recordSend(clock.instant());
        assertTrue(manager.etaSeconds(4).isEmpty());
        clock.advance(Duration.ofSeconds(10));
        manager.recordSend(clock.instant());
        // 2 sends in the 60s window: one send per 30s, position 4 -> 120s
        assertEquals(120L, manager.etaSeconds(4).getAsLong());
    }

    @Test
    void etaForgetsSendsOutsideWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        QueueManager manager = new QueueManager(clock);
        manager.recordSend(clock.instant());
        manager.recordSend(clock.instant());
        clock.advance(Duration.ofSeconds(120));
        assertTrue(manager.etaSeconds(1).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
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

    private static QueueEntry entry(UUID id, String name, int priority, Instant at) {
        return new QueueEntry(id, name, priority, at);
    }
}
