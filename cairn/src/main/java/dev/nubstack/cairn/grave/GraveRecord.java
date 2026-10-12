package dev.nubstack.cairn.grave;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracked death grave. Loot is stored as SNBT strings; world presence is Interaction + ItemDisplay.
 */
public record GraveRecord(
        String id,
        UUID ownerId,
        String ownerName,
        String dimension,
        int x,
        int y,
        int z,
        int ticksRemaining,
        List<String> itemsSnbt,
        UUID interactionId,
        UUID displayId,
        int despawnAnimTicks) {

    public static final int DESPAWN_ANIM_TICKS = 20;

    public GraveRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(itemsSnbt, "itemsSnbt");
        itemsSnbt = List.copyOf(itemsSnbt);
        if (ticksRemaining < 0) {
            throw new IllegalArgumentException("ticksRemaining must be >= 0");
        }
        if (despawnAnimTicks < 0) {
            throw new IllegalArgumentException("despawnAnimTicks must be >= 0");
        }
    }

    public GraveKey key() {
        return new GraveKey(dimension, x, y, z);
    }

    public boolean isEmpty() {
        return itemsSnbt.isEmpty();
    }

    public boolean isDespawning() {
        return despawnAnimTicks > 0;
    }

    public GraveRecord withTicks(int ticks) {
        return new GraveRecord(
                id, ownerId, ownerName, dimension, x, y, z, ticks, itemsSnbt, interactionId, displayId, despawnAnimTicks);
    }

    public GraveRecord withItems(List<String> items) {
        return new GraveRecord(
                id, ownerId, ownerName, dimension, x, y, z, ticksRemaining, items, interactionId, displayId, despawnAnimTicks);
    }

    public GraveRecord withEntities(UUID interaction, UUID display) {
        return new GraveRecord(
                id, ownerId, ownerName, dimension, x, y, z, ticksRemaining, itemsSnbt, interaction, display, despawnAnimTicks);
    }

    public GraveRecord withDespawnAnim(int ticks) {
        return new GraveRecord(
                id, ownerId, ownerName, dimension, x, y, z, ticksRemaining, itemsSnbt, interactionId, displayId, ticks);
    }
}
