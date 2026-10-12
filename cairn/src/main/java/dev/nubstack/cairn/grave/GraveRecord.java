package dev.nubstack.cairn.grave;

import java.util.Objects;
import java.util.UUID;

public record GraveRecord(
        String id,
        UUID ownerId,
        String ownerName,
        String dimension,
        int x,
        int y,
        int z,
        int ticksRemaining) {

    public GraveRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(dimension, "dimension");
        if (ticksRemaining < 0) {
            throw new IllegalArgumentException("ticksRemaining must be >= 0");
        }
    }

    public GraveKey key() {
        return new GraveKey(dimension, x, y, z);
    }

    public GraveRecord withTicks(int ticks) {
        return new GraveRecord(id, ownerId, ownerName, dimension, x, y, z, ticks);
    }
}
