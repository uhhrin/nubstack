package dev.nubstack.aegis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-staff cooldown to blunt double-click double punishments.
 */
public final class StaffCooldown {

    private final Duration cooldown;
    private final Clock clock;
    private final Map<UUID, Instant> lastAction = new ConcurrentHashMap<>();

    public StaffCooldown(Duration cooldown) {
        this(cooldown, Clock.systemUTC());
    }

    public StaffCooldown(Duration cooldown, Clock clock) {
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative");
        }
    }

    /**
     * @return true if the action may proceed
     */
    public boolean tryAcquire(UUID staffId) {
        Objects.requireNonNull(staffId, "staffId");
        if (cooldown.isZero()) {
            return true;
        }
        Instant now = clock.instant();
        Instant previous = lastAction.get(staffId);
        if (previous != null && previous.plus(cooldown).isAfter(now)) {
            return false;
        }
        lastAction.put(staffId, now);
        return true;
    }

    public Duration remaining(UUID staffId) {
        Objects.requireNonNull(staffId, "staffId");
        Instant previous = lastAction.get(staffId);
        if (previous == null || cooldown.isZero()) {
            return Duration.ZERO;
        }
        Instant readyAt = previous.plus(cooldown);
        Instant now = clock.instant();
        if (!readyAt.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, readyAt);
    }
}
