package dev.nubstack.loom;

/** Slot mapping of the read-only inspect chest. */
final class InspectLayout {

    static final int SIZE = 54;
    static final int PLAYER_CONTENTS = 41;
    static final int INFO_SLOT = 49;

    private InspectLayout() {
    }

    /**
     * Chest slot to {@code PlayerInventory#getContents()} index, or -1 for an empty slot.
     * Rows 1-3 are the main inventory, row 4 the hotbar, row 5 armor (head first) and offhand.
     */
    static int sourceIndex(int slot) {
        if (slot >= 0 && slot < 27) {
            return slot + 9;
        }
        if (slot >= 27 && slot < 36) {
            return slot - 27;
        }
        if (slot >= 36 && slot < 40) {
            return 39 - (slot - 36);
        }
        return slot == 41 ? 40 : -1;
    }
}
