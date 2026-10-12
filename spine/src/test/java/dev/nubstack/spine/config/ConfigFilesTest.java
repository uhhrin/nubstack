package dev.nubstack.spine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void copyDefaultIfMissing_writesOnce() throws IOException {
        Path data = tempDir.resolve("plugin");
        boolean first = ConfigFiles.copyDefaultIfMissing(data, "config.yml", () ->
                new ByteArrayInputStream("a: 1\n".getBytes(StandardCharsets.UTF_8)));
        assertTrue(first);
        Path file = data.resolve("config.yml");
        assertEquals("a: 1\n", Files.readString(file));

        Files.writeString(file, "a: changed\n");
        boolean second = ConfigFiles.copyDefaultIfMissing(data, "config.yml", () ->
                new ByteArrayInputStream("a: 1\n".getBytes(StandardCharsets.UTF_8)));
        assertFalse(second);
        assertEquals("a: changed\n", Files.readString(file));
    }
}
