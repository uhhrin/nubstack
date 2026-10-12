package dev.nubstack.cull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Entity count and type mix of one loaded chunk. */
public record ChunkLoad(String world, int chunkX, int chunkZ, int count, int cap, Map<String, Integer> byType) {

    public static List<ChunkLoad> ranked(List<ChunkLoad> loads) {
        return loads.stream().sorted(Comparator.comparingInt(ChunkLoad::count).reversed()).toList();
    }

    public int over() {
        return Math.max(0, count - cap);
    }

    public String breakdown() {
        return byType.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
