package dev.nubstack.spotlight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LagReport {

    public record ChunkCost(String world, int chunkX, int chunkZ, ChunkCounts counts, int score) {
        public String explain(ChunkWeights weights) {
            return counts.explain(weights);
        }
    }

    private final List<ChunkCost> chunks = new ArrayList<>();

    public void add(ChunkCost cost) {
        chunks.add(cost);
    }

    public List<ChunkCost> top(int limit) {
        return chunks.stream()
                .sorted(Comparator.comparingInt(ChunkCost::score).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    public List<ChunkCost> near(String world, int chunkX, int chunkZ, int radiusChunks, int limit) {
        return chunks.stream()
                .filter(cost -> cost.world().equals(world))
                .filter(cost -> chebyshev(cost.chunkX(), cost.chunkZ(), chunkX, chunkZ) <= radiusChunks)
                .sorted(Comparator.comparingInt(ChunkCost::score).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    public ChunkCost atRank(int rank) {
        if (rank < 1) {
            return null;
        }
        List<ChunkCost> ranked = top(rank);
        if (ranked.size() < rank) {
            return null;
        }
        return ranked.get(rank - 1);
    }

    /** 1-based rank in the full score order, or -1 if missing. */
    public int rankOf(ChunkCost cost) {
        List<ChunkCost> ranked = top(chunks.size());
        for (int i = 0; i < ranked.size(); i++) {
            ChunkCost entry = ranked.get(i);
            if (entry.world().equals(cost.world())
                    && entry.chunkX() == cost.chunkX()
                    && entry.chunkZ() == cost.chunkZ()) {
                return i + 1;
            }
        }
        return -1;
    }

    public int size() {
        return chunks.size();
    }

    private static int chebyshev(int x1, int z1, int x2, int z2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
    }
}
