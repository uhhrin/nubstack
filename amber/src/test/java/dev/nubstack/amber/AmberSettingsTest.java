package dev.nubstack.amber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AmberSettingsTest {

    private static final Path DATA = Path.of("plugins", "amber");

    @Test
    void alwaysExcludesSessionLock() {
        AmberSettings settings = AmberSettings.of(DATA, "snapshots", 5, "world", List.of(), true);
        assertEquals(Set.of("session.lock"), settings.exclude());
        assertEquals(DATA.resolve("snapshots"), settings.snapshotDir());
    }

    @Test
    void mergesConfiguredExcludes() {
        AmberSettings settings = AmberSettings.of(DATA, "snapshots", 5, "world", List.of("uid.dat"), false);
        assertTrue(settings.exclude().containsAll(Set.of("session.lock", "uid.dat")));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> AmberSettings.of(DATA, "snapshots", 0, "world", List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> AmberSettings.of(DATA, " ", 5, "world", List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> AmberSettings.of(DATA, "snapshots", 5, "", List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> AmberSettings.of(DATA, "snapshots", 5, "world", List.of("a/b"), true));
    }
}
