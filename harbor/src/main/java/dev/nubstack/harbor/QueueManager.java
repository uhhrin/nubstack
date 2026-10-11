
package dev.nubstack.harbor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager {

    private final Map<String, List<QueueEntry>> queues = new ConcurrentHashMap<>();

    public synchronized void enqueue(String server, QueueEntry entry) {
        List<QueueEntry> queue = queues.computeIfAbsent(server, ignored -> new ArrayList<>());
        queue.removeIf(existing -> existing.playerId().equals(entry.playerId()));
        queue.add(entry);
        queue.sort(Comparator
                .comparingInt(QueueEntry::priority).reversed()
                .thenComparing(QueueEntry::enqueuedAt));
    }

    public synchronized boolean leave(UUID playerId) {
        boolean removed = false;
        for (List<QueueEntry> queue : queues.values()) {
            removed |= queue.removeIf(entry -> entry.playerId().equals(playerId));
        }
        return removed;
    }

    public synchronized Optional<QueueEntry> poll(String server) {
        List<QueueEntry> queue = queues.get(server);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(queue.remove(0));
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

    public synchronized int size(String server) {
        return queues.getOrDefault(server, List.of()).size();
    }

    public QueueEntry entry(UUID playerId, String name, int priority) {
        return new QueueEntry(playerId, name, priority, Instant.now());
    }
}
