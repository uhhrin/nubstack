
package dev.nubstack.gatekeep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectionGuardTest {

    @Test
    void blocksBurst() {
        ConnectionGuard guard = new ConnectionGuard(3, Duration.ofSeconds(60));
        assertTrue(guard.allow("1.2.3.4"));
        assertTrue(guard.allow("1.2.3.4"));
        assertTrue(guard.allow("1.2.3.4"));
        assertFalse(guard.allow("1.2.3.4"));
        assertTrue(guard.allow("9.9.9.9"));
    }
}
