package dev.nubstack.amber;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validated view of {@code config.yml}.
 *
 * @param exclude file or folder names skipped while copying; {@code session.lock} is always included
 */
record AmberSettings(Path snapshotDir, int retain, String defaultWorld, Set<String> exclude, boolean movePlayers) {

    static final String SESSION_LOCK = "session.lock";

    static AmberSettings of(
            Path dataFolder,
            String snapshotDir,
            int retain,
            String defaultWorld,
            List<String> exclude,
            boolean movePlayers) {
        if (snapshotDir == null || snapshotDir.isBlank()) {
            throw new IllegalArgumentException("snapshot-dir must not be blank");
        }
        if (retain < 1) {
            throw new IllegalArgumentException("retain must be >= 1");
        }
        if (defaultWorld == null || defaultWorld.isBlank()) {
            throw new IllegalArgumentException("default-world must not be blank");
        }
        Set<String> names = new LinkedHashSet<>();
        names.add(SESSION_LOCK);
        for (String name : exclude) {
            if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
                throw new IllegalArgumentException("exclude entries must be plain file or folder names: " + name);
            }
            names.add(name.trim());
        }
        return new AmberSettings(
                dataFolder.resolve(snapshotDir.trim()), retain, defaultWorld.trim(), Set.copyOf(names), movePlayers);
    }
}
