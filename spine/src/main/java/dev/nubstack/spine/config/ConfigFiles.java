package dev.nubstack.spine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Copies bundled default config files without overwriting operator edits.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /**
     * @return true if the default was written
     */
    public static boolean copyDefaultIfMissing(Path dataFolder, String fileName, Supplier<InputStream> defaultSource) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(defaultSource, "defaultSource");
        if (fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Unsafe config file name: " + fileName);
        }
        try {
            Files.createDirectories(dataFolder);
            Path target = dataFolder.resolve(fileName);
            if (Files.exists(target)) {
                return false;
            }
            try (InputStream input = defaultSource.get()) {
                if (input == null) {
                    throw new ConfigException("Default config stream was null for " + fileName);
                }
                Files.copy(input, target);
            }
            return true;
        } catch (IOException exception) {
            throw new ConfigException("Failed to copy default config " + fileName, exception);
        }
    }
}
