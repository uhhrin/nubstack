package dev.nubstack.spine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpineVersionTest {

    @Test
    void require_trimsVersion() {
        assertEquals("0.1.0", SpineVersion.require(" 0.1.0 "));
    }

    @Test
    void require_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> SpineVersion.require("   "));
    }

    @Test
    void require_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> SpineVersion.require(null));
    }
}
