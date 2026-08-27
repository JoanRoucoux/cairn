package com.roucoux.cairn.domain.exception.business;

/**
 * Thrown when two {@link com.roucoux.cairn.domain.model.Money} amounts in different currencies
 * are combined. Cairn is EUR-only in v1 (see the design spec's non-objectives): this signals a
 * data problem to fail on, not a currency conversion to perform.
 */
public class CurrencyMismatchException extends BusinessException {

    public CurrencyMismatchException(String left, String right) {
        super("cannot combine amounts in different currencies: " + left + " and " + right);
    }
}
