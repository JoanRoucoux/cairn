package com.roucoux.cairn.domain.exception.business;

import java.math.BigDecimal;

public class InsufficientQuantityException extends BusinessException {

    public InsufficientQuantityException(BigDecimal held, BigDecimal requested) {
        super("cannot sell " + requested.stripTrailingZeros().toPlainString() + ": only "
                + held.stripTrailingZeros().toPlainString() + " held");
    }
}
