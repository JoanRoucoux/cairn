package com.roucoux.cairn.domain.exception.business;

import java.util.UUID;

public class CashHoldingTradeException extends BusinessException {

    public CashHoldingTradeException(UUID holdingId) {
        super("holding " + holdingId + " is cash: set its balance instead of buying or selling");
    }
}
