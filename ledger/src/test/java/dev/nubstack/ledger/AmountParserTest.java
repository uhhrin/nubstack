package dev.nubstack.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountParserTest {

    private final AmountParser parser = new AmountParser(new BigDecimal("1000000.00"));

    @Test
    void parsesValidAmount() {
        assertEquals(new BigDecimal("12.50"), parser.parse("12.50"));
        assertEquals(new BigDecimal("7.00"), parser.parse("7"));
    }

    @Test
    void rejectsNonNumeric() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse("abc"));
        assertTrue(ex.getMessage().contains("number"));
    }

    @Test
    void rejectsZeroNegativeAndTooManyDecimals() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("-1"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1.234"));
    }

    @Test
    void parseNonNegativeAllowsZeroButNotNegative() {
        assertEquals(new BigDecimal("0.00"), parser.parseNonNegative("0"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseNonNegative("-0.01"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseNonNegative("abc"));
    }

    @Test
    void rejectsExponentCommasAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1e3"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1,000"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void rejectsAboveMax() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("1000000.01"));
        assertTrue(ex.getMessage().toLowerCase().contains("max"));
    }
}
