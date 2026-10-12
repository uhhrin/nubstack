package dev.nubstack.harbor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PriorityPermissionsTest {

    @TempDir
    Path tempDir;

    @Test
    void readsDottedPermissionKeysFromConfigMap() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, """
                priority-permissions:
                  harbor.priority.vip: 10
                  harbor.priority.staff: 50
                  harbor.priority.donor: 25
                """);
        Map<String, Integer> priorities = PriorityPermissions.load(config);
        assertEquals(10, priorities.get("harbor.priority.vip"));
        assertEquals(50, priorities.get("harbor.priority.staff"));
        assertEquals(25, priorities.get("harbor.priority.donor"));
        assertEquals(3, priorities.size());
    }
}
