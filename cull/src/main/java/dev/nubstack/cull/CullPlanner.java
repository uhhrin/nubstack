package dev.nubstack.cull;

import java.util.Comparator;
import java.util.List;

/** Picks which entities to remove from an over-cap chunk. */
public final class CullPlanner {

    private CullPlanner() {
    }

    /** Hostiles go before animals, then the oldest first. Protected and ineligible entities are never picked. */
    public static <T> List<T> pickVictims(CullSettings settings, List<CullCandidate<T>> candidates, int overflow) {
        if (overflow <= 0) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> candidate.traits().stream().noneMatch(settings.protections()::contains))
                .filter(candidate -> settings.eligible(candidate.type(), candidate.hostile()))
                .sorted(Comparator.comparing((CullCandidate<T> candidate) -> !candidate.hostile())
                        .thenComparing(candidate -> -candidate.ticksLived()))
                .limit(overflow)
                .map(CullCandidate::ref)
                .toList();
    }
}
