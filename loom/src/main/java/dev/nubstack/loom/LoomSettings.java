package dev.nubstack.loom;

import dev.nubstack.spine.config.YamlConfig;
import java.time.Duration;
import java.util.Objects;

/** Sync tuning read from config.yml. Database settings are read separately at enable time. */
record LoomSettings(
        String serverId,
        Duration leaseTtl,
        Duration waitTimeout,
        SyncCoordinator.FailurePolicy failurePolicy) {

    static final int MIN_LOCK_SECONDS = 5;

    LoomSettings {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        Objects.requireNonNull(waitTimeout, "waitTimeout");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
    }

    /**
     * @param fallbackServerId used when {@code server-id} is blank
     * @throws IllegalArgumentException for out-of-range numbers or an unknown policy
     */
    static LoomSettings from(YamlConfig config, String fallbackServerId) {
        String serverId = config.optionalString("server-id").map(String::trim).orElse("");
        if (serverId.isEmpty()) {
            serverId = fallbackServerId;
        }
        if (serverId.length() > 64) {
            throw new IllegalArgumentException("server-id must be at most 64 characters");
        }
        int lockSeconds = config.optionalInt("lock-seconds").orElse(30);
        if (lockSeconds < MIN_LOCK_SECONDS) {
            throw new IllegalArgumentException("lock-seconds must be at least " + MIN_LOCK_SECONDS);
        }
        int waitSeconds = config.optionalInt("wait-seconds").orElse(10);
        if (waitSeconds < 1) {
            throw new IllegalArgumentException("wait-seconds must be at least 1");
        }
        return new LoomSettings(
                serverId,
                Duration.ofSeconds(lockSeconds),
                Duration.ofSeconds(waitSeconds),
                SyncCoordinator.FailurePolicy.parse(config.optionalString("failure-policy").orElse("kick")));
    }
}
