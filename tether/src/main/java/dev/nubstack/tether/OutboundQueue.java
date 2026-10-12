package dev.nubstack.tether;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Bounded Discord outbound queue. Drops with a log line when full (rate-limit backpressure).
 */
public final class OutboundQueue {

    public record Job(String channelId, String content) {
        public Job {
            Objects.requireNonNull(channelId, "channelId");
            Objects.requireNonNull(content, "content");
        }
    }

    private final BlockingQueue<Job> queue;
    private final Logger logger;

    public OutboundQueue(int capacity, Logger logger) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @return true if accepted, false if dropped
     */
    public boolean offer(Job job) {
        Objects.requireNonNull(job, "job");
        boolean accepted = queue.offer(job);
        if (!accepted) {
            logger.warning("tether outbound queue full; dropping Discord message (channel="
                    + job.channelId() + ", len=" + job.content().length() + ")");
        }
        return accepted;
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    /**
     * Drains up to {@code max} jobs to {@code consumer}.
     */
    public int drain(int max, Consumer<Job> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        int sent = 0;
        while (sent < max) {
            Job job = queue.poll();
            if (job == null) {
                break;
            }
            consumer.accept(job);
            sent++;
        }
        return sent;
    }
}
