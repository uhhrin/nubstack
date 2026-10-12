package dev.nubstack.cull;

import java.util.concurrent.atomic.AtomicLong;

/** Counters since plugin enable. Not persisted. */
public final class CullStats {

    private final AtomicLong cancelledSpawns = new AtomicLong();
    private final AtomicLong culledEntities = new AtomicLong();

    public void spawnCancelled() {
        cancelledSpawns.incrementAndGet();
    }

    public void culled(int count) {
        culledEntities.addAndGet(count);
    }

    public long cancelledSpawns() {
        return cancelledSpawns.get();
    }

    public long culledEntities() {
        return culledEntities.get();
    }
}
