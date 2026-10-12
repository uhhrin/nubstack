package dev.nubstack.spotlight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanBudgetTest {

    @Test
    void takesUpToBudgetPerTick() {
        List<String> remaining = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
        List<String> batch = ScanBudget.take(remaining, 2);
        assertEquals(List.of("a", "b"), batch);
        assertEquals(List.of("c", "d", "e"), remaining);
        assertEquals(List.of("c", "d"), ScanBudget.take(remaining, 2));
        assertEquals(List.of("e"), ScanBudget.take(remaining, 2));
        assertTrueEmpty(remaining);
    }

    @Test
    void ticksNeededRoundsUp() {
        assertEquals(3, ScanBudget.ticksNeeded(5, 2));
        assertEquals(1, ScanBudget.ticksNeeded(2, 2));
        assertEquals(0, ScanBudget.ticksNeeded(0, 2));
    }

    private static void assertTrueEmpty(List<String> remaining) {
        assertEquals(0, remaining.size());
    }
}
