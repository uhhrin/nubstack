package dev.nubstack.gatekeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nubstack.spine.config.ConfigException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GatekeepConfigTest {

    @TempDir
    Path dir;

    private GatekeepConfig load(String yaml) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, yaml);
        return GatekeepConfig.load(file);
    }

    @Test
    void emptyConfigUsesDefaults() throws Exception {
        GatekeepConfig config = load("{}\n");
        assertEquals(5, config.settings().maxAttempts());
        assertEquals(Duration.ofSeconds(60), config.settings().window());
        assertTrue(config.settings().ipv6Group64());
        ConnectionGuard guard = new ConnectionGuard(config.settings(), Clock.systemUTC());
        assertTrue(guard.isExempt("127.0.0.1"));
        assertTrue(guard.isExempt("::1"));
        assertFalse(guard.isExempt("172.17.0.2"));
        assertTrue(config.denyMessage().contains("Too many connection attempts"));
    }

    @Test
    void readsExemptListAndDockerOption() throws Exception {
        GatekeepConfig config = load("""
                max-attempts: 9
                window-seconds: 30
                ipv6-group-64: false
                exempt-docker-private: true
                exempt:
                  - 203.0.113.0/24
                  - 2001:db8::/32
                deny-message: "<yellow>Slow down</yellow>"
                """);
        ConnectionGuard guard = new ConnectionGuard(config.settings(), Clock.systemUTC());
        assertEquals(9, config.settings().maxAttempts());
        assertFalse(config.settings().ipv6Group64());
        assertTrue(guard.isExempt("203.0.113.9"));
        assertTrue(guard.isExempt("2001:db8::5"));
        assertTrue(guard.isExempt("172.17.0.2"));
        assertTrue(guard.isExempt("192.168.1.1"));
        assertFalse(guard.isExempt("127.0.0.1"));
        assertEquals("<yellow>Slow down</yellow>", config.denyMessage());
    }

    @Test
    void invalidValuesFailLoudly() {
        assertThrows(ConfigException.class, () -> load("exempt:\n  - not-an-ip\n"));
        assertThrows(ConfigException.class, () -> load("max-attempts: 0\n"));
        assertThrows(ConfigException.class, () -> load("window-seconds: 0\n"));
        assertThrows(ConfigException.class, () -> load("exempt: nope\n"));
    }
}
