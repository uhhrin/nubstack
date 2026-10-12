package dev.nubstack.gatekeep;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ConnectionGuard {

    public record Settings(int maxAttempts, Duration window, boolean ipv6Group64, List<Cidr> exempt) {
        public Settings {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1");
            }
            if (window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("window must be positive");
            }
            exempt = List.copyOf(exempt);
        }
    }

    public record Decision(
            boolean allowed, String key, int attempts, Duration window, Duration retryAfter, String supportCode) {

        public String logLine(String ip) {
            return "event=gatekeep.deny ip=" + ip + " attempts=" + attempts + " window=" + window.toSeconds() + "s";
        }
    }

    public record Entry(String key, int attempts, int reputation, Duration retryAfter) {
    }

    // shortcut: reputation is forgotten after 10 quiet windows to bound memory; upgrade if long-term scoring is needed.
    private static final int REPUTATION_WINDOWS = 10;

    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final AtomicLong denied = new AtomicLong();
    private volatile Settings settings;
    private volatile Instant lastSweep;

    public ConnectionGuard(int maxAttempts, Duration window) {
        this(new Settings(maxAttempts, window, true, List.of()), Clock.systemUTC());
    }

    public ConnectionGuard(Settings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        this.lastSweep = clock.instant();
    }

    public void configure(Settings settings) {
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    public boolean allow(String ip) {
        return check(ip).allowed();
    }

    public Decision check(String ip) {
        Settings current = settings;
        InetAddress address = Cidr.address(ip);
        Instant now = clock.instant();
        if (isExempt(address, current)) {
            return new Decision(true, ip, 0, current.window(), Duration.ZERO, "");
        }
        sweep(now, current);
        String key = keyOf(address, current);
        Decision[] result = new Decision[1];
        states.compute(key, (k, existing) -> {
            State state = existing == null ? new State() : existing;
            synchronized (state) {
                state.trim(now, current.window());
                state.lastSeen = now;
                int limit = effectiveLimit(current.maxAttempts(), state.score);
                if (state.times.size() >= limit) {
                    state.score += 5;
                    result[0] = new Decision(false, key, state.times.size(), current.window(),
                            state.retryAfter(now, current.window()), supportCode(key));
                } else {
                    state.times.addLast(now);
                    state.score = Math.max(0, state.score - 1);
                    result[0] = new Decision(true, key, state.times.size(), current.window(), Duration.ZERO, "");
                }
            }
            return state;
        });
        if (!result[0].allowed()) {
            denied.incrementAndGet();
        }
        return result[0];
    }

    public Optional<Entry> inspect(String ip) {
        Settings current = settings;
        String key = keyOf(Cidr.address(ip), current);
        State state = states.get(key);
        return state == null ? Optional.empty() : Optional.of(entryOf(key, state, current));
    }

    public List<Entry> snapshots(int limit) {
        Settings current = settings;
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            out.add(entryOf(entry.getKey(), entry.getValue(), current));
        }
        out.sort(Comparator.comparingInt(Entry::attempts).reversed().thenComparing(Entry::key));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }

    public boolean clear(String ip) {
        return states.remove(keyOf(Cidr.address(ip), settings)) != null;
    }

    public int clearAll() {
        int count = states.size();
        states.clear();
        return count;
    }

    public boolean isExempt(String ip) {
        return isExempt(Cidr.address(ip), settings);
    }

    public int trackedCount() {
        return states.size();
    }

    public long deniedTotal() {
        return denied.get();
    }

    public static int effectiveLimit(int maxAttempts, int reputation) {
        return Math.max(1, maxAttempts - Math.max(0, reputation / 10));
    }

    public static String supportCode(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return String.format("GK-%02X%02X", hash[0] & 0xFF, hash[1] & 0xFF);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Entry entryOf(String key, State state, Settings current) {
        Instant now = clock.instant();
        synchronized (state) {
            int attempts = state.countWithin(now, current.window());
            Duration retry = attempts >= effectiveLimit(current.maxAttempts(), state.score)
                    ? state.retryAfter(now, current.window())
                    : Duration.ZERO;
            return new Entry(key, attempts, state.score, retry);
        }
    }

    private void sweep(Instant now, Settings current) {
        Duration ttl = current.window().multipliedBy(REPUTATION_WINDOWS);
        if (now.isBefore(lastSweep.plus(current.window()))) {
            return;
        }
        lastSweep = now;
        for (String key : List.copyOf(states.keySet())) {
            states.computeIfPresent(key, (k, state) -> state.lastSeen.plus(ttl).isBefore(now) ? null : state);
        }
    }

    private static boolean isExempt(InetAddress address, Settings current) {
        for (Cidr range : current.exempt()) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static String keyOf(InetAddress address, Settings current) {
        if (current.ipv6Group64() && address instanceof Inet6Address) {
            byte[] b = address.getAddress();
            return String.format("%x:%x:%x:%x::",
                    ((b[0] & 0xFF) << 8) | (b[1] & 0xFF),
                    ((b[2] & 0xFF) << 8) | (b[3] & 0xFF),
                    ((b[4] & 0xFF) << 8) | (b[5] & 0xFF),
                    ((b[6] & 0xFF) << 8) | (b[7] & 0xFF));
        }
        return address.getHostAddress();
    }

    private static final class State {
        private final Deque<Instant> times = new ArrayDeque<>();
        private int score;
        private Instant lastSeen = Instant.EPOCH;

        void trim(Instant now, Duration window) {
            while (!times.isEmpty() && Duration.between(times.peekFirst(), now).compareTo(window) > 0) {
                times.removeFirst();
            }
        }

        int countWithin(Instant now, Duration window) {
            int count = 0;
            for (Instant at : times) {
                if (Duration.between(at, now).compareTo(window) <= 0) {
                    count++;
                }
            }
            return count;
        }

        Duration retryAfter(Instant now, Duration window) {
            if (times.isEmpty()) {
                return Duration.ZERO;
            }
            Duration left = Duration.between(now, times.peekFirst().plus(window));
            return left.isNegative() ? Duration.ZERO : left;
        }
    }
}
