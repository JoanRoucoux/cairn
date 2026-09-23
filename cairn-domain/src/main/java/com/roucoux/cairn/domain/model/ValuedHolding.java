package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record ValuedHolding(
        Holding holding, Instrument instrument, Account account, Optional<Quote> quote, Optional<Quote> previousQuote) {

    private static final int RATIO_SCALE = 10;
    private static final int FUND_FRESHNESS_DAYS = 4;
    private static final Duration CRYPTO_FRESHNESS = Duration.ofHours(6);

    public ValuedHolding {
        Objects.requireNonNull(holding, "holding");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(previousQuote, "previousQuote");
    }

    public Optional<Money> marketValue() {
        return quote.map(this::valueAt);
    }

    private Money valueAt(Quote quote) {
        return new Money(holding.quantity().multiply(quote.price()), quote.currency());
    }

    public Optional<Money> unrealizedGain() {
        if (instrument.isPricedAtPar()) {
            return quote.map(q -> new Money(BigDecimal.ZERO, q.currency()));
        }
        return quote.flatMap(q -> holding.costBasis()
                .map(cost -> valueAt(q).minus(new Money(holding.quantity().multiply(cost), q.currency()))));
    }

    public Optional<BigDecimal> unrealizedGainRatio() {
        if (instrument.isPricedAtPar()) {
            return quote.map(q -> BigDecimal.ZERO);
        }
        return quote.flatMap(q -> holding.costBasis()
                .filter(cost -> cost.signum() != 0)
                .map(cost -> q.price().subtract(cost).divide(cost, RATIO_SCALE, RoundingMode.HALF_UP)));
    }

    public Optional<Money> dayChange() {
        return quote.flatMap(q -> previousClose()
                .map(previous -> new Money(holding.quantity().multiply(q.price().subtract(previous)), q.currency())));
    }

    public Optional<BigDecimal> dayChangeRatio() {
        return quote.flatMap(q -> previousClose()
                .filter(previous -> previous.signum() != 0)
                .map(previous -> q.price().subtract(previous).divide(previous, RATIO_SCALE, RoundingMode.HALF_UP)));
    }

    private Optional<BigDecimal> previousClose() {
        return previousQuote.map(Quote::price);
    }

    public boolean isStale(Clock clock) {
        if (quote.isEmpty()) {
            return false;
        }
        Quote q = quote.get();
        return switch (instrument.assetClass()) {
            case CASH -> false;
            case CRYPTO -> q.fetchedAt().isBefore(clock.instant().minus(CRYPTO_FRESHNESS));
            case FUND -> q.asOf().isBefore(LocalDate.now(clock).minusDays(FUND_FRESHNESS_DAYS));
            case EQUITY, ETF -> q.asOf().isBefore(previousBusinessDay(LocalDate.now(clock)));
        };
    }

    private static LocalDate previousBusinessDay(LocalDate from) {
        LocalDate day = from.minusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.minusDays(1);
        }
        return day;
    }
}
