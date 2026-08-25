package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValuedHoldingStalenessTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    // Le 2026-08-21 est un vendredi : le "jour ouvre precedent" est le jeudi 20.
    private static final Instant FRIDAY_NOON =
            LocalDate.of(2026, 8, 21).atTime(12, 0).atZone(PARIS).toInstant();
    private static final Clock CLOCK = Clock.fixed(FRIDAY_NOON, PARIS);

    @Test
    void anEquityQuotedYesterdayIsFresh() {
        assertThat(line(AssetClass.EQUITY, LocalDate.of(2026, 8, 20), FRIDAY_NOON)
                        .isStale(CLOCK))
                .isFalse();
    }

    @Test
    void anEquityQuotedTwoDaysAgoIsStale() {
        assertThat(line(AssetClass.EQUITY, LocalDate.of(2026, 8, 19), FRIDAY_NOON)
                        .isStale(CLOCK))
                .isTrue();
    }

    @Test
    void aFundIsFreshUpToFourCalendarDays() {
        assertThat(line(AssetClass.FUND, LocalDate.of(2026, 8, 17), FRIDAY_NOON).isStale(CLOCK))
                .isFalse();
        assertThat(line(AssetClass.FUND, LocalDate.of(2026, 8, 16), FRIDAY_NOON).isStale(CLOCK))
                .isTrue();
    }

    @Test
    void cryptoStalenessIsReadOnTheFetchTimeNotTheDate() {
        Instant fiveHoursAgo = FRIDAY_NOON.minus(Duration.ofHours(5));
        Instant sevenHoursAgo = FRIDAY_NOON.minus(Duration.ofHours(7));

        assertThat(line(AssetClass.CRYPTO, LocalDate.of(2026, 8, 21), fiveHoursAgo)
                        .isStale(CLOCK))
                .isFalse();
        assertThat(line(AssetClass.CRYPTO, LocalDate.of(2026, 8, 21), sevenHoursAgo)
                        .isStale(CLOCK))
                .isTrue();
    }

    @Test
    void cashIsNeverStale() {
        assertThat(line(AssetClass.CASH, LocalDate.of(2020, 1, 1), FRIDAY_NOON.minus(Duration.ofDays(900)))
                        .isStale(CLOCK))
                .isFalse();
    }

    private static ValuedHolding line(AssetClass assetClass, LocalDate asOf, Instant fetchedAt) {
        UUID instrumentId = UUID.randomUUID();
        PriceSource source = assetClass == AssetClass.CASH ? PriceSource.MANUAL : PriceSource.YAHOO;
        String ref = source == PriceSource.MANUAL ? null : "TEST.PA";
        Instrument instrument = new Instrument(instrumentId, "Test", null, "EUR", assetClass, source, ref, null);
        Account account = new Account(UUID.randomUUID(), "Test", AccountType.CTO, "Test");
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, BigDecimal.ONE, null);
        Quote quote = new Quote(instrumentId, asOf, BigDecimal.TEN, "EUR", source, fetchedAt);
        return new ValuedHolding(holding, instrument, account, quote, null);
    }
}
