
package dev.nubstack.harbor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record QueueEntry(UUID playerId, String name, int priority, Instant enqueuedAt) {
    public QueueEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt");
    }
}
