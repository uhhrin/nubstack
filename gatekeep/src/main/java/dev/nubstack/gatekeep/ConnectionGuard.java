
package dev.nubstack.gatekeep;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionGuard {

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Map<String, Integer> reputation = new ConcurrentHashMap<>();

    public ConnectionGuard(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    public boolean allow(String ip) {
        Instant now = Instant.now();
        Deque<Instant> times = attempts.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && Duration.between(times.peekFirst(), now).compareTo(window) > 0) {
                times.removeFirst();
            }
            int score = reputation.getOrDefault(ip, 0);
            int limit = Math.max(1, maxAttempts - Math.max(0, score / 10));
            if (times.size() >= limit) {
                reputation.merge(ip, 5, Integer::sum);
                return false;
            }
            times.addLast(now);
            reputation.merge(ip, -1, (a, b) -> Math.max(0, a + b));
            return true;
        }
    }

    public int reputation(String ip) {
        return reputation.getOrDefault(ip, 0);
    }
}
