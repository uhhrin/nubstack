package dev.nubstack.spotlight;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw counts collected from one loaded chunk before scoring.
 */
public record ChunkCounts(
        int entities,
        int hoppers,
        int minecarts,
        int redstone,
        int itemFrames,
        int villagers) {

    public int score(ChunkWeights weights) {
        return entities * weights.entities()
                + hoppers * weights.hoppers()
                + minecarts * weights.minecarts()
                + redstone * weights.redstone()
                + itemFrames * weights.itemFrames()
                + villagers * weights.villagers();
    }

    public String explain(ChunkWeights weights) {
        List<String> parts = new ArrayList<>();
        parts.add(entities + " entities x" + weights.entities());
        if (hoppers > 0) {
            parts.add(hoppers + " hoppers x" + weights.hoppers());
        }
        if (minecarts > 0) {
            parts.add(minecarts + " minecarts x" + weights.minecarts());
        }
        if (redstone > 0) {
            parts.add(redstone + " redstone x" + weights.redstone());
        }
        if (itemFrames > 0) {
            parts.add(itemFrames + " item frames x" + weights.itemFrames());
        }
        if (villagers > 0) {
            parts.add(villagers + " villagers x" + weights.villagers());
        }
        return String.join(" + ", parts) + " = " + score(weights);
    }
}
