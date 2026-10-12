package dev.nubstack.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CurrencyFormatTest {

    private final CurrencyFormat format = new CurrencyFormat("Credits");

    @Test
    void addsCommasAndCurrencyName() {
        assertEquals("1,234,567.80 Credits", format.format(new BigDecimal("1234567.8")));
        assertEquals("0.00 Credits", format.format(BigDecimal.ZERO));
    }

    @Test
    void compactDropsEmptyDecimals() {
        assertEquals("500", format.compact(new BigDecimal("500.00")));
        assertEquals("1,000.5", format.compact(new BigDecimal("1000.50")));
    }
}
