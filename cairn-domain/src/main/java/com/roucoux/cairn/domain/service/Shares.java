package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class Shares {

    static final int SCALE = 10;

    private Shares() {}

    static BigDecimal share(Money part, Money total) {
        return total.amount().signum() == 0
                ? BigDecimal.ZERO
                : part.amount().divide(total.amount(), SCALE, RoundingMode.HALF_UP);
    }
}
