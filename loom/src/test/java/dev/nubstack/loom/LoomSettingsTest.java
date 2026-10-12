package dev.nubstack.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nubstack.spine.config.YamlConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoomSettingsTest {

    @TempDir
    Path dir;

    @Test
    void defaults_whenOnlyDatabaseIsConfigured() throws IOException {
        LoomSettings settings = LoomSettings.from(load("database:\n  username: x\n"), "port-25565");
        assertEquals("port-25565", settings.serverId());
        assertEquals(Duration.ofSeconds(30), settings.leaseTtl());
        assertEquals(Duration.ofSeconds(10), settings.waitTimeout());
        assertEquals(SyncCoordinator.FailurePolicy.KICK, settings.failurePolicy());
    }

    @Test
    void readsConfiguredValues() throws IOException {
        LoomSettings settings = LoomSettings.from(load("""
                server-id: hub
                lock-seconds: 45
                wait-seconds: 6
                failure-policy: join-empty
                """), "fallback");
        assertEquals("hub", settings.serverId());
        assertEquals(Duration.ofSeconds(45), settings.leaseTtl());
        assertEquals(Duration.ofSeconds(6), settings.waitTimeout());
        assertEquals(SyncCoordinator.FailurePolicy.JOIN_EMPTY, settings.failurePolicy());
    }

    @Test
    void rejectsInvalidValues() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> LoomSettings.from(load("lock-seconds: 1\n"), "s"));
        assertThrows(IllegalArgumentException.class, () -> LoomSettings.from(load("wait-seconds: 0\n"), "s"));
        assertThrows(IllegalArgumentException.class, () -> LoomSettings.from(load("failure-policy: nope\n"), "s"));
        assertThrows(IllegalArgumentException.class,
                () -> LoomSettings.from(load("server-id: " + "x".repeat(65) + "\n"), "s"));
    }

    private YamlConfig load(String yaml) throws IOException {
        Path file = Files.createTempFile(dir, "config", ".yml");
        Files.writeString(file, yaml);
        return YamlConfig.load(file);
    }
}
