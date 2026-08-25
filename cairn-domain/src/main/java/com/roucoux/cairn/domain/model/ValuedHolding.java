package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

public record ValuedHolding(Holding holding, Instrument instrument, Account account, Quote quote, Quote previousQuote) {

    private static final int RATIO_SCALE = 10;

    public ValuedHolding {
        Objects.requireNonNull(holding, "holding");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(quote, "quote");
    }

    public Money marketValue() {
        return new Money(holding.quantity().multiply(quote.price()), quote.currency());
    }

    public Optional<Money> unrealizedGain() {
        return holding.costBasis()
                .map(cost -> marketValue().minus(new Money(holding.quantity().multiply(cost), quote.currency())));
    }

    public Optional<BigDecimal> unrealizedGainRatio() {
        return holding.costBasis()
                .filter(cost -> cost.signum() != 0)
                .map(cost -> quote.price().subtract(cost).divide(cost, RATIO_SCALE, RoundingMode.HALF_UP));
    }

    public Optional<Money> dayChange() {
        return previousClose()
                .map(previous ->
                        new Money(holding.quantity().multiply(quote.price().subtract(previous)), quote.currency()));
    }

    public Optional<BigDecimal> dayChangeRatio() {
        return previousClose()
                .filter(previous -> previous.signum() != 0)
                .map(previous -> quote.price().subtract(previous).divide(previous, RATIO_SCALE, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> previousClose() {
        return Optional.ofNullable(previousQuote).map(Quote::price);
    }
}
