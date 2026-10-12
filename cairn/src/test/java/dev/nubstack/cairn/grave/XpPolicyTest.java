package dev.nubstack.cairn.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class XpPolicyTest {

    @Test
    void parse_acceptsKnownNamesCaseInsensitive() {
        assertEquals(XpPolicy.DROP, XpPolicy.parse("drop"));
        assertEquals(XpPolicy.KEEP, XpPolicy.parse("KEEP"));
        assertEquals("keep", XpPolicy.KEEP.configName());
    }

    @Test
    void parse_rejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> XpPolicy.parse("grave"));
    }
}
