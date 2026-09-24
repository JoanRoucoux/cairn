package com.roucoux.cairn.domain.exception.business;

/** Thrown when a cash balance is set to a negative amount (mapped to 422). */
public class NegativeCashBalanceException extends BusinessException {

    public NegativeCashBalanceException() {
        super("cash balance amount must not be negative");
    }
}
