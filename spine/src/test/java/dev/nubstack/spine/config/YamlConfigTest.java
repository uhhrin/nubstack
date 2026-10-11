package dev.nubstack.spine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void load_readsNestedValues() throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, """
                database:
                  url: jdbc:h2:mem:test
                  pool-size: 4
                enabled: true
                """);

        YamlConfig config = YamlConfig.load(file);

        assertEquals("jdbc:h2:mem:test", config.requireString("database.url"));
        assertEquals(4, config.requireInt("database.pool-size"));
        assertTrue(config.requireBoolean("enabled"));
        assertFalse(config.optionalString("missing").isPresent());
    }

    @Test
    void requireString_rejectsBlank() throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "name: '   '\n");

        YamlConfig config = YamlConfig.load(file);

        assertThrows(ConfigException.class, () -> config.requireString("name"));
    }

    @Test
    void load_rejectsMissingFile() {
        Path missing = tempDir.resolve("nope.yml");
        assertThrows(ConfigException.class, () -> YamlConfig.load(missing));
    }

    @Test
    void load_rejectsNonMapRoot() throws IOException {
        Path file = tempDir.resolve("list.yml");
        Files.writeString(file, "- just\n- a\n- list\n");

        assertThrows(ConfigException.class, () -> YamlConfig.load(file));
    }
}
