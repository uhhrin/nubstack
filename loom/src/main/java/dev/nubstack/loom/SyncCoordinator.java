package dev.nubstack.loom;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned inventory handoff state machine. Callers drive retries with a fake or real clock.
 */
public final class SyncCoordinator {

    public enum FailurePolicy {
        KICK,
        JOIN_EMPTY,
        KEEP_LOCAL;

        public static FailurePolicy parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return KICK;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "kick" -> KICK;
                case "join-empty", "join_empty", "empty" -> JOIN_EMPTY;
                case "keep-local", "keep_local", "local" -> KEEP_LOCAL;
                default -> throw new IllegalArgumentException("Unknown failure-policy: " + raw);
            };
        }
    }

    public enum JoinDecision {
        READY,
        WAIT,
        FAILED_KICK,
        FAILED_JOIN_EMPTY,
        FAILED_KEEP_LOCAL
    }

    private final PlayerDataStore store;
    private final Clock clock;
    private final String serverId;
    private final Duration leaseTtl;
    private final Duration waitTimeout;
    private final FailurePolicy failurePolicy;

    public SyncCoordinator(
            PlayerDataStore store,
            Clock clock,
            String serverId,
            Duration leaseTtl,
            Duration waitTimeout,
            FailurePolicy failurePolicy) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.leaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl");
        this.waitTimeout = Objects.requireNonNull(waitTimeout, "waitTimeout");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must not be blank");
        }
        if (leaseTtl.isNegative() || leaseTtl.isZero()) {
            throw new IllegalArgumentException("leaseTtl must be positive");
        }
        if (waitTimeout.isNegative()) {
            throw new IllegalArgumentException("waitTimeout must not be negative");
        }
    }

    public JoinDecision pollJoin(UUID playerId, Instant joinStartedAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(joinStartedAt, "joinStartedAt");
        if (store.tryAcquire(playerId, serverId, leaseTtl)) {
            return JoinDecision.READY;
        }
        return blocked(joinStartedAt);
    }

    /** Decision while the lease cannot be taken (held elsewhere or the database failed). */
    public JoinDecision blocked(Instant joinStartedAt) {
        Objects.requireNonNull(joinStartedAt, "joinStartedAt");
        if (Duration.between(joinStartedAt, clock.instant()).compareTo(waitTimeout) < 0) {
            return JoinDecision.WAIT;
        }
        return switch (failurePolicy) {
            case KICK -> JoinDecision.FAILED_KICK;
            case JOIN_EMPTY -> JoinDecision.FAILED_JOIN_EMPTY;
            case KEEP_LOCAL -> JoinDecision.FAILED_KEEP_LOCAL;
        };
    }

    public int waitSecondsRemaining(Instant joinStartedAt) {
        Objects.requireNonNull(joinStartedAt, "joinStartedAt");
        long elapsed = Duration.between(joinStartedAt, clock.instant()).getSeconds();
        return (int) Math.max(0L, waitTimeout.getSeconds() - elapsed);
    }

    /** Stored blob for a player whose lease this server holds, or empty for a first join. */
    public Optional<String> loadBlob(UUID playerId) {
        Optional<String> blob = store.find(playerId)
                .filter(PlayerDataStore.Snapshot::hasInventory)
                .map(PlayerDataStore.Snapshot::inventoryBlob);
        blob.ifPresent(b -> store.audit("load", playerId, serverId, "bytes=" + b.length()));
        return blob;
    }

    public boolean saveAndRelease(UUID playerId, String inventoryBlob) {
        return store.saveAndRelease(playerId, serverId, inventoryBlob);
    }

    public boolean saveKeepingLease(UUID playerId, String inventoryBlob) {
        return store.saveKeepingLease(playerId, serverId, inventoryBlob, leaseTtl);
    }

    /** Drops this server's lease without writing data. */
    public boolean release(UUID playerId) {
        return store.release(playerId, serverId);
    }

    public boolean renew(UUID playerId) {
        return store.renew(playerId, serverId, leaseTtl);
    }

    public Duration waitTimeout() {
        return waitTimeout;
    }

    public Duration leaseTtl() {
        return leaseTtl;
    }

    public FailurePolicy failurePolicy() {
        return failurePolicy;
    }

    public String serverId() {
        return serverId;
    }

    public PlayerDataStore store() {
        return store;
    }
}
