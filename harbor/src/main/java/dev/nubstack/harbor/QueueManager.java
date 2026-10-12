package dev.nubstack.harbor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager {

    public enum EnqueueResult {
        QUEUED,
        FULL
    }

    // shortcut: ETA uses one proxy-wide send rate over a fixed 60s window, not per server; upgrade if queues drain at very different speeds.
    private static final Duration RATE_WINDOW = Duration.ofSeconds(60);
    private static final int MIN_SENDS_FOR_ETA = 2;

    private final Clock clock;
    private final Map<String, List<QueueEntry>> queues = new ConcurrentHashMap<>();
    private final Set<String> paused = ConcurrentHashMap.newKeySet();
    private final Deque<Instant> sends = new ArrayDeque<>();
    private volatile int maxQueueSize = Integer.MAX_VALUE;

    public QueueManager() {
        this(Clock.systemUTC());
    }

    public QueueManager(Clock clock) {
        this.clock = clock;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = Math.max(1, maxQueueSize);
    }

    public synchronized EnqueueResult enqueue(String server, QueueEntry entry) {
        List<QueueEntry> queue = queues.computeIfAbsent(server, ignored -> new ArrayList<>());
        boolean already = queue.removeIf(existing -> existing.playerId().equals(entry.playerId()));
        if (!already && queue.size() >= maxQueueSize) {
            if (queue.isEmpty()) {
                queues.remove(server);
            }
            return EnqueueResult.FULL;
        }
        queue.add(entry);
        queue.sort(Comparator
                .comparingInt(QueueEntry::priority).reversed()
                .thenComparing(QueueEntry::enqueuedAt));
        return EnqueueResult.QUEUED;
    }

    public synchronized boolean leave(UUID playerId) {
        boolean removed = false;
        for (List<QueueEntry> queue : queues.values()) {
            removed |= queue.removeIf(entry -> entry.playerId().equals(playerId));
        }
        queues.values().removeIf(List::isEmpty);
        return removed;
    }

    public synchronized Optional<QueueEntry> poll(String server) {
        List<QueueEntry> queue = queues.get(server);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        QueueEntry head = queue.remove(0);
        if (queue.isEmpty()) {
            queues.remove(server);
        }
        return Optional.of(head);
    }

    public synchronized Optional<QueueEntry> peek(String server) {
        List<QueueEntry> queue = queues.get(server);
        return queue == null || queue.isEmpty() ? Optional.empty() : Optional.of(queue.get(0));
    }

    public synchronized int position(String server, UUID playerId) {
        List<QueueEntry> queue = queues.getOrDefault(server, List.of());
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).playerId().equals(playerId)) {
                return i + 1;
            }
        }
        return -1;
    }

    public synchronized Optional<Integer> priorityOf(String server, UUID playerId) {
        for (QueueEntry entry : queues.getOrDefault(server, List.of())) {
            if (entry.playerId().equals(playerId)) {
                return Optional.of(entry.priority());
            }
        }
        return Optional.empty();
    }

    public synchronized int size(String server) {
        return queues.getOrDefault(server, List.of()).size();
    }

    public synchronized List<QueueEntry> clear(String server) {
        List<QueueEntry> removed = queues.remove(server);
        return removed == null ? List.of() : List.copyOf(removed);
    }

    public synchronized Optional<String> serverOf(UUID playerId) {
        for (Map.Entry<String, List<QueueEntry>> entry : queues.entrySet()) {
            for (QueueEntry queued : entry.getValue()) {
                if (queued.playerId().equals(playerId)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    public synchronized Set<String> queuedServers() {
        return new LinkedHashSet<>(queues.keySet());
    }

    public void pause(String server) {
        paused.add(server);
    }

    public void resume(String server) {
        paused.remove(server);
    }

    public boolean isPaused(String server) {
        return paused.contains(server);
    }

    public synchronized void recordSend(Instant at) {
        sends.addLast(at);
        trimSends();
    }

    public synchronized OptionalLong etaSeconds(int position) {
        trimSends();
        if (position < 1 || sends.size() < MIN_SENDS_FOR_ETA) {
            return OptionalLong.empty();
        }
        double perSecond = sends.size() / (double) RATE_WINDOW.toSeconds();
        return OptionalLong.of((long) Math.ceil(position / perSecond));
    }

    public QueueEntry entry(UUID playerId, String name, int priority) {
        return new QueueEntry(playerId, name, priority, clock.instant());
    }

    private void trimSends() {
        Instant cutoff = clock.instant().minus(RATE_WINDOW);
        while (!sends.isEmpty() && sends.peekFirst().isBefore(cutoff)) {
            sends.removeFirst();
        }
    }
}
