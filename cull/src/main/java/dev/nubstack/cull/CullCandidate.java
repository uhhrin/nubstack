package dev.nubstack.cull;

import java.util.Set;

/** Entity facts the planner needs, detached from Bukkit so selection stays unit testable. */
public record CullCandidate<T>(T ref, String type, boolean hostile, long ticksLived, Set<Protection> traits) {
}
