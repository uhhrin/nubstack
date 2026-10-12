
package dev.nubstack.cull;

public final class ChunkEntityLimiter {

    private final int maxPerChunk;

    public ChunkEntityLimiter(int maxPerChunk) {
        this.maxPerChunk = maxPerChunk;
    }

    public boolean allows(int currentCount) {
        return currentCount < maxPerChunk;
    }

    public int overflow(int currentCount) {
        return Math.max(0, currentCount - maxPerChunk);
    }
}
