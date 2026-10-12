package dev.nubstack.harbor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerHealthRegistry {

    public record Health(boolean online, int players, int maxPlayers, Instant checkedAt) {}

    private volatile int failThreshold;
    private volatile int recoverThreshold;
    private final Map<String, Mutable> health = new ConcurrentHashMap<>();

    public ServerHealthRegistry() {
        this(2, 2);
    }

    public ServerHealthRegistry(int failThreshold, int recoverThreshold) {
        this.failThreshold = Math.max(1, failThreshold);
        this.recoverThreshold = Math.max(1, recoverThreshold);
    }

    public void setThresholds(int failThreshold, int recoverThreshold) {
        this.failThreshold = Math.max(1, failThreshold);
        this.recoverThreshold = Math.max(1, recoverThreshold);
    }

    public void update(String server, boolean online, int players, int maxPlayers) {
        probe(server, online, players, maxPlayers, Instant.now());
    }

    public void probe(String server, boolean reachable, int players, int maxPlayers, Instant checkedAt) {
        Mutable state = health.computeIfAbsent(server, ignored -> new Mutable());
        synchronized (state) {
            state.checkedAt = checkedAt;
            if (reachable) {
                state.players = players;
                state.maxPlayers = maxPlayers;
                state.failStreak = 0;
                state.recoverStreak++;
                if (!state.online && state.recoverStreak >= recoverThreshold) {
                    state.online = true;
                } else if (state.online) {
                    state.recoverStreak = recoverThreshold;
                }
            } else {
                state.recoverStreak = 0;
                state.failStreak++;
                if (state.online && state.failStreak >= failThreshold) {
                    state.online = false;
                } else if (!state.online) {
                    state.failStreak = failThreshold;
                }
            }
        }
    }

    public boolean isAvailable(String server) {
        Mutable value = health.get(server);
        if (value == null) {
            return true;
        }
        synchronized (value) {
            return value.online && value.players < value.maxPlayers;
        }
    }

    public String statusLabel(String server) {
        Mutable value = health.get(server);
        if (value == null) {
            return "unknown";
        }
        synchronized (value) {
            if (!value.online) {
                return "unhealthy";
            }
            if (value.players >= value.maxPlayers) {
                return "full";
            }
            return "healthy";
        }
    }

    public Health get(String server) {
        Mutable value = health.get(server);
        if (value == null) {
            return null;
        }
        synchronized (value) {
            return new Health(value.online, value.players, value.maxPlayers, value.checkedAt);
        }
    }

    private static final class Mutable {
        private boolean online = true;
        private int players;
        private int maxPlayers = Integer.MAX_VALUE;
        private Instant checkedAt = Instant.EPOCH;
        private int failStreak;
        private int recoverStreak;
    }
}
