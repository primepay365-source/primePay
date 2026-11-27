package com.primepay.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Money helpers. Amounts are BigDecimal with 2 decimals, rounded half-up. Never double. */
public final class Money {
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** "₹ 1,25,000.00" for messages and notifications. */
    public static String format(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(INDIA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "₹ " + format.format(value);
    }
}
