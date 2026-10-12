package dev.nubstack.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Parses player-entered money amounts. Rejects junk before any async work runs.
 */
public final class AmountParser {

    private final BigDecimal maxAmount;

    public AmountParser(BigDecimal maxAmount) {
        this.maxAmount = Objects.requireNonNull(maxAmount, "maxAmount").setScale(2, RoundingMode.HALF_UP);
        if (this.maxAmount.signum() <= 0) {
            throw new IllegalArgumentException("maxAmount must be positive");
        }
    }

    public BigDecimal parse(String raw) {
        BigDecimal value = parseNonNegative(raw);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return value;
    }

    /** Like {@link #parse} but allows zero (for /ledger set). */
    public BigDecimal parseNonNegative(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Amount must be a number");
        }
        String text = raw.trim();
        if (!text.matches("\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("Amount must be a number");
        }
        int dot = text.indexOf('.');
        if (dot >= 0 && text.length() - dot - 1 > 2) {
            throw new IllegalArgumentException("Amount can have at most 2 decimals");
        }
        BigDecimal value = new BigDecimal(text).setScale(2, RoundingMode.UNNECESSARY);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (value.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("Amount is above max (" + maxAmount.toPlainString() + ")");
        }
        return value;
    }
}
