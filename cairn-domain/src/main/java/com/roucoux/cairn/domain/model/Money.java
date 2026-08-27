package com.roucoux.cairn.domain.model;

import com.roucoux.cairn.domain.exception.business.CurrencyMismatchException;
import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public static final String EUR = "EUR";

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    public static Money eur(BigDecimal amount) {
        return new Money(amount, EUR);
    }

    public static Money zeroEur() {
        return eur(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        if (!currency.equals(other.currency())) {
            throw new CurrencyMismatchException(currency, other.currency());
        }
        return new Money(amount.add(other.amount()), currency);
    }

    public Money minus(Money other) {
        if (!currency.equals(other.currency())) {
            throw new CurrencyMismatchException(currency, other.currency());
        }
        return new Money(amount.subtract(other.amount()), currency);
    }
}
