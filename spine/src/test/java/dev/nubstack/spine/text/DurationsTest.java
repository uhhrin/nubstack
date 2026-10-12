package dev.nubstack.spine.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parse_supportsCommonUnits() {
        assertEquals(Duration.ofSeconds(30), Durations.parse("30s").orElseThrow());
        assertEquals(Duration.ofHours(12), Durations.parse("12h").orElseThrow());
        assertEquals(Duration.ofDays(7), Durations.parse("7d").orElseThrow());
        assertEquals(Duration.ofDays(14), Durations.parse("2w").orElseThrow());
    }

    @Test
    void parse_blankMeansEmpty() {
        assertTrue(Durations.parse("").isEmpty());
        assertTrue(Durations.parse(null).isEmpty());
    }

    @Test
    void parse_rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("forever"));
    }

    @Test
    void humanize_andRemaining() {
        assertEquals("7 days", Durations.humanize(Duration.ofDays(7)));
        assertEquals("11h 58m", Durations.remaining(Duration.ofHours(11).plusMinutes(58)));
    }
}
