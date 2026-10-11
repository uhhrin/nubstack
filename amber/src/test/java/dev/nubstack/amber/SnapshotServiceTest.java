
package dev.nubstack.amber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotServiceTest {
    @TempDir Path temp;

    @Test
    void createsAndPrunes() throws Exception {
        Path world = temp.resolve("world");
        Files.createDirectories(world);
        Files.writeString(world.resolve("level.dat"), "data");
        SnapshotService service = new SnapshotService(temp.resolve("snaps"), 1);
        service.create("one", world);
        service.create("two", world);
        assertEquals(1, service.list().size());
        assertTrue(Files.exists(service.list().getFirst().resolve("level.dat")));
    }
}
