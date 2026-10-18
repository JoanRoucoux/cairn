package com.roucoux.cairn.application.json;

import java.math.BigDecimal;

public final class DecimalBounds {

    private static final int MAX_SCALE = 12;
    private static final int MAX_INTEGER_DIGITS = 16;
    private static final int MIN_RAW_SCALE = -16;
    private static final int MAX_RAW_SCALE = 32;

    private DecimalBounds() {}

    public static boolean isWithinBounds(BigDecimal value) {
        if (value.scale() < MIN_RAW_SCALE || value.scale() > MAX_RAW_SCALE) {
            return false;
        }
        BigDecimal significant = value.stripTrailingZeros();
        return significant.scale() <= MAX_SCALE && significant.precision() - significant.scale() <= MAX_INTEGER_DIGITS;
    }
}
