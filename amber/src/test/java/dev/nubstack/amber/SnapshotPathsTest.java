package dev.nubstack.amber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotPathsTest {
    @TempDir Path temp;

    @Test
    void resolvesPlainFilenameInsideRoot() {
        Path root = temp.resolve("snaps");
        Path resolved = SnapshotPaths.resolve(root, "20260101-120000-world");
        assertEquals(root.resolve("20260101-120000-world").normalize(), resolved);
        assertTrue(resolved.startsWith(root.toAbsolutePath().normalize()));
    }

    @Test
    void rejectsParentTraversal() {
        Path root = temp.resolve("snaps");
        assertThrows(IllegalArgumentException.class, () -> SnapshotPaths.resolve(root, "../outside"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotPaths.resolve(root, "a/../../outside"));
    }

    @Test
    void rejectsAbsoluteAndSeparators() {
        Path root = temp.resolve("snaps");
        assertThrows(IllegalArgumentException.class, () -> SnapshotPaths.resolve(root, "/tmp/evil"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotPaths.resolve(root, "foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotPaths.resolve(root, "foo\\bar"));
    }

    @Test
    void rejectsBlank() {
        Path root = temp.resolve("snaps");
        assertThrows(IllegalArgumentException.class, () -> SnapshotPaths.resolve(root, "  "));
        assertThrows(IllegalArgumentException.class, () -> SnapshotPaths.resolve(root, null));
    }
}
