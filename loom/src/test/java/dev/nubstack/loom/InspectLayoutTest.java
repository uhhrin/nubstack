package dev.nubstack.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InspectLayoutTest {

    @Test
    void layout_mapsMainRowsThenHotbarThenArmorAndOffhand() {
        assertEquals(9, InspectLayout.sourceIndex(0));
        assertEquals(35, InspectLayout.sourceIndex(26));
        assertEquals(0, InspectLayout.sourceIndex(27));
        assertEquals(8, InspectLayout.sourceIndex(35));
        assertEquals(39, InspectLayout.sourceIndex(36));
        assertEquals(36, InspectLayout.sourceIndex(39));
        assertEquals(40, InspectLayout.sourceIndex(41));
    }

    @Test
    void layout_leavesOtherSlotsEmptyAndNeverRepeatsASource() {
        Set<Integer> seen = new HashSet<>();
        for (int slot = 0; slot < InspectLayout.SIZE; slot++) {
            int source = InspectLayout.sourceIndex(slot);
            if (source >= 0) {
                assertTrue(source < InspectLayout.PLAYER_CONTENTS, "slot " + slot);
                assertTrue(seen.add(source), "duplicate source " + source);
            }
        }
        assertEquals(InspectLayout.PLAYER_CONTENTS, seen.size());
        assertEquals(-1, InspectLayout.sourceIndex(40));
        assertEquals(-1, InspectLayout.sourceIndex(InspectLayout.INFO_SLOT));
        assertEquals(-1, InspectLayout.sourceIndex(54));
        assertEquals(-1, InspectLayout.sourceIndex(-1));
    }
}
