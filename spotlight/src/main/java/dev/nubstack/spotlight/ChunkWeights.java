package dev.nubstack.spotlight;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Per-signal score multipliers for a chunk lag scan.
 */
public record ChunkWeights(
        int entities,
        int hoppers,
        int minecarts,
        int redstone,
        int itemFrames,
        int villagers) {

    public static ChunkWeights fromConfig(FileConfiguration config) {
        return new ChunkWeights(
                config.getInt("entity-weight", 1),
                config.getInt("hopper-weight", 8),
                config.getInt("minecart-weight", 4),
                config.getInt("redstone-weight", 2),
                config.getInt("item-frame-weight", 3),
                config.getInt("villager-weight", 5));
    }
}
