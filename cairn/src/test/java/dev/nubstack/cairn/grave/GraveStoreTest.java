package dev.nubstack.cairn.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraveStoreTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @TempDir
    Path dir;

    @Test
    void saveThenLoad_roundTrips() throws IOException {
        Path file = dir.resolve("graves.json");
        GraveRecord grave = new GraveRecord("4", OWNER, "Steve", "minecraft:the_nether", 1, 70, -9, 1200);
        GraveStore.save(file, List.of(grave));
        assertEquals(List.of(grave), GraveStore.load(file));
    }

    @Test
    void missingFile_isEmpty() throws IOException {
        assertTrue(GraveStore.load(dir.resolve("nope.json")).isEmpty());
    }

    @Test
    void corruptFile_isQuarantinedAndTreatedAsEmpty() throws IOException {
        Path file = dir.resolve("graves.json");
        Files.writeString(file, "{{{{");
        assertTrue(GraveStore.load(file).isEmpty());
        assertTrue(Files.exists(dir.resolve("graves.json.corrupt")));
        assertTrue(Files.notExists(file));
    }
}
