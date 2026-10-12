package dev.nubstack.cairn.grave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraveKeyItemTest {

    @Test
    void isValidGraveId_rejectsBlank() {
        assertTrue(GraveKeyItem.isValidGraveId("1"));
        assertFalse(GraveKeyItem.isValidGraveId(""));
        assertFalse(GraveKeyItem.isValidGraveId(null));
        assertFalse(GraveKeyItem.isValidGraveId("   "));
    }

    @Test
    void emptyGrave_shouldDespawn() {
        GraveRecord empty = new GraveRecord(
                "1",
                java.util.UUID.randomUUID(),
                "Steve",
                "minecraft:overworld",
                0,
                64,
                0,
                100,
                java.util.List.of(),
                null,
                null,
                0);
        assertTrue(empty.isEmpty());
        assertFalse(empty.isDespawning());
        assertTrue(empty.withDespawnAnim(GraveRecord.DESPAWN_ANIM_TICKS).isDespawning());
    }
}
