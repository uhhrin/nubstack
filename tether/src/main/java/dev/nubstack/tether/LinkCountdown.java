package dev.nubstack.tether;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * In-memory deadlines for the action bar countdown. No SQL on tick; the deadline is
 * recorded when the card is shown.
 */
public final class LinkCountdown {

    private final Clock clock;
    private final Map<UUID, Long> deadlines = new ConcurrentHashMap<>();

    public LinkCountdown(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void track(UUID playerId, long remainingSeconds) {
        Objects.requireNonNull(playerId, "playerId");
        deadlines.put(playerId, clock.instant().getEpochSecond() + remainingSeconds);
    }

    public void stop(UUID playerId) {
        deadlines.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public boolean isTracking(UUID playerId) {
        return deadlines.containsKey(playerId);
    }

    /**
     * Reports remaining seconds for live deadlines and removes expired ones, calling {@code onExpire} once each.
     */
    public void tick(BiConsumer<UUID, Long> onTick, Consumer<UUID> onExpire) {
        long now = clock.instant().getEpochSecond();
        for (Map.Entry<UUID, Long> entry : deadlines.entrySet()) {
            long left = entry.getValue() - now;
            if (left <= 0) {
                if (deadlines.remove(entry.getKey(), entry.getValue())) {
                    onExpire.accept(entry.getKey());
                }
            } else {
                onTick.accept(entry.getKey(), left);
            }
        }
    }
}
