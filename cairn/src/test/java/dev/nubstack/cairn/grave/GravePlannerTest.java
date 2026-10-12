package dev.nubstack.cairn.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GravePlannerTest {

    @Test
    void assignSlots_fitsSingleChest() {
        List<String> items = List.of("a", "b", "c");
        GravePlanner.PackPlan<String> plan = GravePlanner.assignSlots(items, 27, 2);
        assertEquals(1, plan.chests().size());
        assertEquals(3, plan.chests().getFirst().size());
        assertTrue(plan.leftover().isEmpty());
    }

    @Test
    void assignSlots_overflowUsesSecondChestThenDrops() {
        List<Integer> items = java.util.stream.IntStream.range(0, 60).boxed().toList();
        GravePlanner.PackPlan<Integer> plan = GravePlanner.assignSlots(items, 27, 2);
        assertEquals(2, plan.chests().size());
        assertEquals(27, plan.chests().get(0).size());
        assertEquals(27, plan.chests().get(1).size());
        assertEquals(6, plan.leftover().size());
    }

    @Test
    void findSafePos_prefersDeathThenAbove() {
        Set<String> air = Set.of("0,64,0", "0,65,0");
        Optional<GravePlanner.IntPos> pos = GravePlanner.findSafePos(
                new GravePlanner.IntPos(0, 64, 0),
                -64,
                320,
                4,
                (x, y, z) -> air.contains(x + "," + y + "," + z));
        assertEquals(Optional.of(new GravePlanner.IntPos(0, 64, 0)), pos);
    }

    @Test
    void findSafePos_searchesNearbyWhenDeathBlocked() {
        Set<String> air = new HashSet<>();
        air.add("1,70,0");
        Optional<GravePlanner.IntPos> pos = GravePlanner.findSafePos(
                new GravePlanner.IntPos(0, 64, 0),
                -64,
                320,
                4,
                (x, y, z) -> air.contains(x + "," + y + "," + z));
        assertEquals(Optional.of(new GravePlanner.IntPos(1, 70, 0)), pos);
    }

    @Test
    void findSafePos_voidDeathIsClampedIntoBuildHeight() {
        Optional<GravePlanner.IntPos> pos = GravePlanner.findSafePos(
                new GravePlanner.IntPos(5, -200, 5),
                -64,
                320,
                4,
                (x, y, z) -> y == -64);
        assertEquals(Optional.of(new GravePlanner.IntPos(5, -64, 5)), pos);
    }

    @Test
    void findSafePos_neverReturnsOutsideBuildHeight() {
        Optional<GravePlanner.IntPos> pos = GravePlanner.findSafePos(
                new GravePlanner.IntPos(0, 64, 0),
                0,
                100,
                3,
                (x, y, z) -> y > 100 || y < 0);
        assertTrue(pos.isEmpty());
    }

    @Test
    void findSafePos_emptyWhenNowhereSafe() {
        Optional<GravePlanner.IntPos> pos = GravePlanner.findSafePos(
                new GravePlanner.IntPos(0, 0, 0),
                0,
                10,
                2,
                (x, y, z) -> false);
        assertTrue(pos.isEmpty());
    }
}
