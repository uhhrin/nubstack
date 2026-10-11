
package dev.nubstack.spotlight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LagReport {

    public record ChunkCost(String world, int chunkX, int chunkZ, int entities, int hoppers, int score) {}

    private final List<ChunkCost> chunks = new ArrayList<>();

    public void add(ChunkCost cost) {
        chunks.add(cost);
    }

    public List<ChunkCost> top(int limit) {
        return chunks.stream()
                .sorted(Comparator.comparingInt(ChunkCost::score).reversed())
                .limit(limit)
                .toList();
    }
}
