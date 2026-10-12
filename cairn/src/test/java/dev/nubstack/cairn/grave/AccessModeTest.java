package dev.nubstack.cairn.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessModeTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void ownerMode_allowsOwnerAndAdminsOnly() {
        assertTrue(AccessMode.OWNER.allows(OWNER, OWNER, false));
        assertTrue(AccessMode.OWNER.allows(OWNER, OTHER, true));
        assertFalse(AccessMode.OWNER.allows(OWNER, OTHER, false));
    }

    @Test
    void publicMode_allowsEveryone() {
        assertTrue(AccessMode.PUBLIC.allows(OWNER, OTHER, false));
    }

    @Test
    void parse_isCaseInsensitiveAndRejectsUnknown() {
        assertEquals(AccessMode.PUBLIC, AccessMode.parse("Public"));
        assertEquals("owner", AccessMode.OWNER.configName());
        assertThrows(IllegalArgumentException.class, () -> AccessMode.parse("friends"));
    }
}
