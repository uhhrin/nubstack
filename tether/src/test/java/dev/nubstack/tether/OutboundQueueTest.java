package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class OutboundQueueTest {

    @Test
    void offer_dropsAndLogsWhenFull() {
        Logger logger = Logger.getLogger("tether-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        List<String> logged = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        OutboundQueue queue = new OutboundQueue(1, logger);

        assertTrue(queue.offer(new OutboundQueue.Job("1", "a")));
        assertFalse(queue.offer(new OutboundQueue.Job("1", "b")));

        assertEquals(1, queue.size());
        assertEquals(1, logged.size());
        assertTrue(logged.get(0).contains("dropping"));
    }

    @Test
    void drain_respectsMaxAndOrder() {
        OutboundQueue queue = new OutboundQueue(5, Logger.getAnonymousLogger());
        queue.offer(new OutboundQueue.Job("1", "a"));
        queue.offer(new OutboundQueue.Job("1", "b"));
        queue.offer(new OutboundQueue.Job("1", "c"));

        List<String> sent = new ArrayList<>();
        assertEquals(2, queue.drain(2, job -> sent.add(job.content())));

        assertEquals(List.of("a", "b"), sent);
        assertEquals(1, queue.size());
    }
}
