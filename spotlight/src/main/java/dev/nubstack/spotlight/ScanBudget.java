package dev.nubstack.spotlight;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers for spreading a scan across ticks.
 */
public final class ScanBudget {

    private ScanBudget() {}

    /** Removes and returns up to {@code budget} items from the front of {@code remaining}. */
    public static <T> List<T> take(List<T> remaining, int budget) {
        int n = Math.min(Math.max(0, budget), remaining.size());
        List<T> batch = new ArrayList<>(remaining.subList(0, n));
        remaining.subList(0, n).clear();
        return batch;
    }

    public static int ticksNeeded(int total, int perTick) {
        if (total <= 0 || perTick <= 0) {
            return 0;
        }
        return (total + perTick - 1) / perTick;
    }
}
