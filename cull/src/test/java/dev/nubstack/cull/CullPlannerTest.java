package dev.nubstack.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CullPlannerTest {

    private static final CullSettings ALL = new CullSettings(
            40, Map.of(), CullMode.ALL, Set.of(), Set.of("ITEM_FRAME"),
            EnumSet.allOf(Protection.class), 200L);

    private static CullCandidate<String> mob(String id, String type, boolean hostile, long age, Protection... traits) {
        Set<Protection> set = EnumSet.noneOf(Protection.class);
        set.addAll(List.of(traits));
        return new CullCandidate<>(id, type, hostile, age, set);
    }

    @Test
    void hostilesGoBeforeAnimalsThenOldestFirst() {
        List<CullCandidate<String>> all = List.of(
                mob("oldCow", "COW", false, 900),
                mob("youngZombie", "ZOMBIE", true, 10),
                mob("oldZombie", "ZOMBIE", true, 500));
        assertEquals(List.of("oldZombie", "youngZombie"), CullPlanner.pickVictims(ALL, all, 2));
        assertEquals(List.of("oldZombie", "youngZombie", "oldCow"), CullPlanner.pickVictims(ALL, all, 5));
    }

    @Test
    void protectedTraitsAreSkippedByDefault() {
        List<CullCandidate<String>> all = List.of(
                mob("named", "COW", false, 100, Protection.NAMED),
                mob("pet", "WOLF", false, 100, Protection.TAMED),
                mob("leashed", "HORSE", false, 100, Protection.LEASHED),
                mob("villager", "VILLAGER", false, 100, Protection.VILLAGERS),
                mob("stand", "ARMOR_STAND", false, 100, Protection.ARMOR_STANDS),
                mob("boat", "BOAT", false, 100, Protection.VEHICLES),
                mob("plain", "PIG", false, 1));
        assertEquals(List.of("plain"), CullPlanner.pickVictims(ALL, all, 10));
    }

    @Test
    void disablingAProtectionMakesThatTraitRemovable() {
        CullSettings noVillagerProtection = new CullSettings(
                40, Map.of(), CullMode.ALL, Set.of(), Set.of(),
                EnumSet.complementOf(EnumSet.of(Protection.VILLAGERS)), 200L);
        List<CullCandidate<String>> all = List.of(mob("villager", "VILLAGER", false, 100, Protection.VILLAGERS));
        assertEquals(List.of("villager"), CullPlanner.pickVictims(noVillagerProtection, all, 1));
    }

    @Test
    void deniedAndIneligibleTypesAreSkipped() {
        CullSettings hostileOnly = new CullSettings(
                40, Map.of(), CullMode.HOSTILE, Set.of(), Set.of("CREEPER"),
                EnumSet.allOf(Protection.class), 200L);
        List<CullCandidate<String>> all = List.of(
                mob("cow", "COW", false, 100),
                mob("creeper", "CREEPER", true, 100),
                mob("zombie", "ZOMBIE", true, 100));
        assertEquals(List.of("zombie"), CullPlanner.pickVictims(hostileOnly, all, 5));
    }

    @Test
    void nothingToRemoveWhenOverflowNotPositive() {
        List<CullCandidate<String>> all = List.of(mob("pig", "PIG", false, 1));
        assertTrue(CullPlanner.pickVictims(ALL, all, 0).isEmpty());
        assertTrue(CullPlanner.pickVictims(ALL, all, -2).isEmpty());
    }
}
