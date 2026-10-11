
package dev.nubstack.loom;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TransferLock {

    private final Map<UUID, Instant> locks = new ConcurrentHashMap<>();
    private final Duration ttl;

    public TransferLock(Duration ttl) {
        this.ttl = ttl;
    }

    public boolean tryLock(UUID playerId) {
        Instant now = Instant.now();
        locks.entrySet().removeIf(e -> e.getValue().isBefore(now));
        Instant expires = now.plus(ttl);
        Instant existing = locks.putIfAbsent(playerId, expires);
        if (existing == null) {
            return true;
        }
        if (existing.isBefore(now)) {
            locks.put(playerId, expires);
            return true;
        }
        return false;
    }

    public void unlock(UUID playerId) {
        locks.remove(playerId);
    }
}
