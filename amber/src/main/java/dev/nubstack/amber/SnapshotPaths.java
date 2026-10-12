package dev.nubstack.amber;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves snapshot filenames strictly under the snapshot root.
 */
public final class SnapshotPaths {

    private SnapshotPaths() {
    }

    public static Path resolve(Path snapshotRoot, String filename) {
        Objects.requireNonNull(snapshotRoot, "snapshotRoot");
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Snapshot name must not be blank");
        }
        if (filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")
                || filename.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unsafe snapshot name: " + filename);
        }
        Path root = snapshotRoot.toAbsolutePath().normalize();
        Path candidate = root.resolve(filename).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new IllegalArgumentException("Snapshot path escapes snapshot directory");
        }
        if (!candidate.getFileName().toString().equals(filename)) {
            throw new IllegalArgumentException("Unsafe snapshot name: " + filename);
        }
        return candidate;
    }
}
