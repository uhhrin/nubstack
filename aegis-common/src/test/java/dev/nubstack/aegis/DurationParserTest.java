package dev.nubstack.aegis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationParserTest {

    @Test
    void parse_supportsCommonUnits() {
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s").orElseThrow());
        assertEquals(Duration.ofHours(12), DurationParser.parse("12h").orElseThrow());
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d").orElseThrow());
        assertEquals(Duration.ofDays(14), DurationParser.parse("2w").orElseThrow());
    }

    @Test
    void parse_blankMeansPermanent() {
        assertTrue(DurationParser.parse("").isEmpty());
        assertTrue(DurationParser.parse(null).isEmpty());
    }

    @Test
    void parse_rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("forever"));
    }
}
