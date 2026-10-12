package dev.nubstack.cairn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nubstack.cairn.grave.AccessMode;
import dev.nubstack.cairn.grave.XpPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CairnConfigTest {

    @TempDir
    Path dir;

    @Test
    void missingFile_createsDefaults() throws IOException {
        Path file = dir.resolve("config/cairn.json");
        CairnConfig config = CairnConfig.loadOrCreate(file);
        assertTrue(Files.exists(file));
        assertEquals(15, config.expireMinutes());
        assertEquals(3, config.maxGravesPerPlayer());
        assertEquals(AccessMode.OWNER, config.accessMode());
        assertEquals(XpPolicy.DROP, config.xpPolicy());
        assertEquals(15 * 60 * 20, config.expireTicks());
    }

    @Test
    void existingFile_isRead_andMissingKeysKeepDefaults() throws IOException {
        Path file = dir.resolve("cairn.json");
        Files.writeString(file, "{\"expireMinutes\":30,\"accessMode\":\"public\",\"xpPolicy\":\"keep\"}");
        CairnConfig config = CairnConfig.loadOrCreate(file);
        assertEquals(30, config.expireMinutes());
        assertEquals(AccessMode.PUBLIC, config.accessMode());
        assertEquals(XpPolicy.KEEP, config.xpPolicy());
        assertEquals(3, config.maxGravesPerPlayer());
    }

    @Test
    void outOfRangeNumbers_areClamped() throws IOException {
        Path file = dir.resolve("cairn.json");
        Files.writeString(file, "{\"expireMinutes\":-5,\"maxGravesPerPlayer\":0,\"searchRadius\":999}");
        CairnConfig config = CairnConfig.loadOrCreate(file);
        assertEquals(1, config.expireMinutes());
        assertEquals(1, config.maxGravesPerPlayer());
        assertEquals(16, config.searchRadius());
    }

    @Test
    void invalidEnum_failsLoudly() throws IOException {
        Path file = dir.resolve("cairn.json");
        Files.writeString(file, "{\"accessMode\":\"friends\"}");
        assertThrows(IllegalArgumentException.class, () -> CairnConfig.loadOrCreate(file));
    }

    @Test
    void malformedJson_isIoException() throws IOException {
        Path file = dir.resolve("cairn.json");
        Files.writeString(file, "[not an object");
        assertThrows(IOException.class, () -> CairnConfig.loadOrCreate(file));
    }
}
