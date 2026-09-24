package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** A part's share of a total, shared by every per-class/per-envelope breakdown. */
final class Shares {

    static final int SCALE = 10;

    private Shares() {}

    static BigDecimal share(Money part, Money total) {
        return total.amount().signum() == 0
                ? BigDecimal.ZERO
                : part.amount().divide(total.amount(), SCALE, RoundingMode.HALF_UP);
    }
}
