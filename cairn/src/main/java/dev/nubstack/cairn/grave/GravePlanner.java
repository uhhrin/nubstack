package dev.nubstack.cairn.grave;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure placement and packing helpers for death graves.
 */
public final class GravePlanner {

    public static final int CHEST_SLOTS = 27;

    private GravePlanner() {
    }

    public record IntPos(int x, int y, int z) {
    }

    public record PackPlan<T>(List<List<T>> chests, List<T> leftover) {
    }

    @FunctionalInterface
    public interface BlockPredicate {
        boolean test(int x, int y, int z);
    }

    public static <T> PackPlan<T> assignSlots(List<T> items, int slotsPerChest, int maxChests) {
        Objects.requireNonNull(items, "items");
        if (slotsPerChest <= 0 || maxChests < 0) {
            throw new IllegalArgumentException("slotsPerChest and maxChests must be non-negative; slots > 0");
        }
        List<List<T>> chests = new ArrayList<>();
        List<T> leftover = new ArrayList<>();
        int index = 0;
        while (index < items.size() && chests.size() < maxChests) {
            int end = Math.min(index + slotsPerChest, items.size());
            chests.add(List.copyOf(items.subList(index, end)));
            index = end;
        }
        if (index < items.size()) {
            leftover.addAll(items.subList(index, items.size()));
        }
        return new PackPlan<>(List.copyOf(chests), List.copyOf(leftover));
    }

    /**
     * Search death position, upward column, then expanding horizontal rings for air.
     */
    public static Optional<IntPos> findSafePos(
            IntPos death,
            int minY,
            int maxY,
            int searchRadius,
            BlockPredicate isAir) {
        Objects.requireNonNull(death, "death");
        Objects.requireNonNull(isAir, "isAir");
        if (searchRadius < 0) {
            throw new IllegalArgumentException("searchRadius must be >= 0");
        }
        int startY = Math.max(minY, Math.min(death.y(), maxY));
        if (isAir.test(death.x(), startY, death.z())) {
            return Optional.of(new IntPos(death.x(), startY, death.z()));
        }
        for (int y = startY + 1; y <= maxY; y++) {
            if (isAir.test(death.x(), y, death.z())) {
                return Optional.of(new IntPos(death.x(), y, death.z()));
            }
        }
        for (int y = startY - 1; y >= minY; y--) {
            if (isAir.test(death.x(), y, death.z())) {
                return Optional.of(new IntPos(death.x(), y, death.z()));
            }
        }
        for (int r = 1; r <= searchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int x = death.x() + dx;
                    int z = death.z() + dz;
                    for (int y = Math.max(minY, startY - r); y <= Math.min(maxY, startY + r + 8); y++) {
                        if (isAir.test(x, y, z)) {
                            return Optional.of(new IntPos(x, y, z));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
