package dev.nubstack.cull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validated cull configuration with per-world caps and type rules. */
public record CullSettings(
        int defaultMax,
        Map<String, Integer> worldMax,
        CullMode mode,
        Set<String> allow,
        Set<String> deny,
        Set<Protection> protections,
        long intervalTicks) {

    public static final int DEFAULT_MAX = 40;
    public static final long DEFAULT_INTERVAL_TICKS = 200L;

    public static CullSettings of(
            int max,
            Map<String, Integer> worldMax,
            CullMode mode,
            Collection<String> allow,
            Collection<String> deny,
            Set<Protection> protections,
            long intervalTicks) {
        Map<String, Integer> worlds = new HashMap<>();
        worldMax.forEach((name, cap) -> {
            if (cap != null && cap > 0) {
                worlds.put(name, cap);
            }
        });
        return new CullSettings(
                max > 0 ? max : DEFAULT_MAX,
                Map.copyOf(worlds),
                mode,
                upper(allow),
                upper(deny),
                protections.isEmpty() ? EnumSet.noneOf(Protection.class) : EnumSet.copyOf(protections),
                intervalTicks > 0 ? intervalTicks : DEFAULT_INTERVAL_TICKS);
    }

    public int capFor(String world) {
        return worldMax.getOrDefault(world, defaultMax);
    }

    public boolean eligible(String type, boolean hostile) {
        String key = type.toUpperCase(Locale.ROOT);
        if (deny.contains(key)) {
            return false;
        }
        return switch (mode) {
            case ALL -> true;
            case HOSTILE -> hostile;
            case CUSTOM -> allow.contains(key);
        };
    }

    private static Set<String> upper(Collection<String> values) {
        Set<String> out = new HashSet<>();
        for (String value : values) {
            out.add(value.trim().toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(out);
    }
}
