package dev.nubstack.ledger;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

public final class CurrencyFormat {

    private final String currencyName;
    private final DecimalFormat numberFormat;
    private final DecimalFormat compactFormat;

    public CurrencyFormat(String currencyName) {
        this.currencyName = Objects.requireNonNull(currencyName, "currencyName");
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        this.numberFormat = new DecimalFormat("#,##0.00", symbols);
        this.compactFormat = new DecimalFormat("#,##0.##", symbols);
    }

    /** Comma grouped, two decimals, with currency name: {@code 1,234.50 Credits}. */
    public synchronized String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return numberFormat.format(amount) + " " + currencyName;
    }

    /** Number only, without trailing empty decimals: {@code 500.00} becomes {@code 500}. */
    public synchronized String compact(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return compactFormat.format(amount);
    }

    public String currencyName() {
        return currencyName;
    }
}
