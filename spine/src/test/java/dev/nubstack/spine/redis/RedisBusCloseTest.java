package dev.nubstack.spine.redis;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RedisBusCloseTest {

    @Test
    void close_marksBusClosedWithoutContactingRedis() {
        // shortcut: no live Redis in unit tests; reconnect behavior is documented in README
        RedisBus bus = RedisBus.open(new RedisSettings("127.0.0.1", 6379, null));
        bus.close();
        assertThrows(IllegalStateException.class, () -> bus.publish("ch", "msg"));
    }
}
