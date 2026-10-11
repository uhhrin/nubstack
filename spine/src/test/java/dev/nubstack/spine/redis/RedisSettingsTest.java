package dev.nubstack.spine.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.Test;

class RedisSettingsTest {

    @Test
    void toUri_includesHostPortAndPassword() {
        RedisSettings settings = new RedisSettings("cache.local", 6380, "s3cret");
        RedisURI uri = settings.toUri();

        assertEquals("cache.local", uri.getHost());
        assertEquals(6380, uri.getPort());
        assertEquals("s3cret", settings.password());
        assertNotNull(uri.getCredentialsProvider());
    }

    @Test
    void toUri_allowsMissingPassword() {
        RedisSettings settings = new RedisSettings("127.0.0.1", 6379, null);
        RedisURI uri = settings.toUri();

        assertNull(settings.password());
        assertEquals(6379, uri.getPort());
        assertNotNull(uri.getCredentialsProvider());
    }

    @Test
    void rejectsBlankHost() {
        assertThrows(IllegalArgumentException.class, () -> new RedisSettings(" ", 6379, null));
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> new RedisSettings("localhost", 0, null));
    }

    @Test
    void open_createsClientThatCanShutDown() {
        RedisSettings settings = new RedisSettings("127.0.0.1", 6379, null);
        try (RedisBus ignored = RedisBus.open(settings)) {
            assertTrue(true);
        }
    }
}
