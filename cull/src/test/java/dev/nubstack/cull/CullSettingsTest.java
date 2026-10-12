package dev.nubstack.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CullSettingsTest {

    private static CullSettings settings(CullMode mode, List<String> allow, List<String> deny) {
        return CullSettings.of(40, Map.of(), mode, allow, deny, EnumSet.allOf(Protection.class), 200L);
    }

    @Test
    void worldCapOverridesGlobalWithFallback() {
        CullSettings s = CullSettings.of(
                40, Map.of("world_nether", 60), CullMode.ALL, List.of(), List.of(),
                EnumSet.noneOf(Protection.class), 200L);
        assertEquals(60, s.capFor("world_nether"));
        assertEquals(40, s.capFor("world"));
    }

    @Test
    void invalidCapsFallBack() {
        CullSettings s = CullSettings.of(
                0, Map.of("a", 0, "b", -5, "c", 10), CullMode.ALL, List.of(), List.of(),
                EnumSet.noneOf(Protection.class), 0L);
        assertEquals(CullSettings.DEFAULT_MAX, s.defaultMax());
        assertEquals(CullSettings.DEFAULT_MAX, s.capFor("a"));
        assertEquals(CullSettings.DEFAULT_MAX, s.capFor("b"));
        assertEquals(10, s.capFor("c"));
        assertEquals(CullSettings.DEFAULT_INTERVAL_TICKS, s.intervalTicks());
    }

    @Test
    void allModeTakesAnyTypeExceptDenied() {
        CullSettings s = settings(CullMode.ALL, List.of(), List.of("painting"));
        assertTrue(s.eligible("COW", false));
        assertTrue(s.eligible("ZOMBIE", true));
        assertFalse(s.eligible("PAINTING", false));
    }

    @Test
    void hostileModeOnlyTakesHostiles() {
        CullSettings s = settings(CullMode.HOSTILE, List.of(), List.of());
        assertTrue(s.eligible("ZOMBIE", true));
        assertFalse(s.eligible("COW", false));
    }

    @Test
    void customModeOnlyTakesAllowListCaseInsensitive() {
        CullSettings s = settings(CullMode.CUSTOM, List.of("cow", "ITEM"), List.of("item"));
        assertTrue(s.eligible("COW", false));
        assertFalse(s.eligible("ITEM", false));
        assertFalse(s.eligible("ZOMBIE", true));
    }

    @Test
    void customModeWithEmptyAllowListTakesNothing() {
        assertFalse(settings(CullMode.CUSTOM, List.of(), List.of()).eligible("COW", false));
    }

    @Test
    void modeParsesCaseInsensitiveAndRejectsUnknown() {
        assertEquals(Optional.of(CullMode.HOSTILE), CullMode.parse("Hostile"));
        assertEquals(Optional.empty(), CullMode.parse("nope"));
        assertEquals(Optional.empty(), CullMode.parse(null));
    }
}
