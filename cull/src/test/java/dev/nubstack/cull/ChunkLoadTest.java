package dev.nubstack.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkLoadTest {

    private static ChunkLoad load(String world, int x, int count, Map<String, Integer> types) {
        return new ChunkLoad(world, x, 0, count, 40, types);
    }

    @Test
    void rankedSortsByCountDescending() {
        List<ChunkLoad> ranked = ChunkLoad.ranked(List.of(
                load("w", 0, 5, Map.of()), load("w", 1, 50, Map.of()), load("w", 2, 20, Map.of())));
        assertEquals(List.of(1, 2, 0), ranked.stream().map(ChunkLoad::chunkX).toList());
    }

    @Test
    void breakdownListsBiggestTypesFirst() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("COW", 3);
        types.put("ZOMBIE", 12);
        types.put("PIG", 3);
        assertEquals("ZOMBIE x12, COW x3, PIG x3", load("w", 0, 18, types).breakdown());
    }

    @Test
    void overIsZeroUnderCap() {
        assertEquals(0, load("w", 0, 10, Map.of()).over());
        assertEquals(15, load("w", 0, 55, Map.of()).over());
    }
}
