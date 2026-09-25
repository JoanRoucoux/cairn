package com.roucoux.cairn.domain.exception.business;

public class NegativeCashBalanceException extends BusinessException {

    public NegativeCashBalanceException() {
        super("cash balance amount must not be negative");
    }
}
