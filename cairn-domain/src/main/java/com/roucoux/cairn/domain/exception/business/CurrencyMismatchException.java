package com.roucoux.cairn.domain.exception.business;

public class CurrencyMismatchException extends BusinessException {

    public CurrencyMismatchException(String left, String right) {
        super("cannot combine amounts in different currencies: " + left + " and " + right);
    }
}
