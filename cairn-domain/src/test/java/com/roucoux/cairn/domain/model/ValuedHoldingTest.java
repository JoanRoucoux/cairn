package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValuedHoldingTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();

    @Test
    void valuesAHoldingAtQuantityTimesPrice() {
        ValuedHolding line = line(new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("25"), null);

        assertThat(line.marketValue().amount()).isEqualByComparingTo("2500");
    }

    @Test
    void keepsFullPrecisionOnVerySmallQuantities() {
        ValuedHolding bitcoin = line(new BigDecimal("0.00012345"), null, new BigDecimal("81000"), null);

        assertThat(bitcoin.marketValue().amount()).isEqualByComparingTo("9.99945000");
    }

    @Test
    void reportsTheUnrealizedGainWhenTheCostBasisIsKnown() {
        ValuedHolding line = line(new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("25"), null);

        assertThat(line.unrealizedGain()).isPresent();
        assertThat(line.unrealizedGain().orElseThrow().amount()).isEqualByComparingTo("500");
    }

    @Test
    void reportsNoUnrealizedGainWhenTheCostBasisIsUnknown() {
        ValuedHolding shares = line(new BigDecimal("50"), null, new BigDecimal("100.00"), null);

        assertThat(shares.unrealizedGain()).isEmpty();
        assertThat(shares.unrealizedGainRatio()).isEmpty();
    }

    @Test
    void reportsTheDayChangeAgainstThePreviousQuote() {
        ValuedHolding line = line(new BigDecimal("50"), null, new BigDecimal("100.00"), new BigDecimal("101.25"));

        assertThat(line.dayChange().orElseThrow().amount()).isEqualByComparingTo("-62.5");
    }

    @Test
    void reportsNoDayChangeWithoutAPreviousQuote() {
        ValuedHolding line = line(new BigDecimal("50"), null, new BigDecimal("100.00"), null);

        assertThat(line.dayChange()).isEmpty();
        assertThat(line.dayChangeRatio()).isEmpty();
    }

    private static ValuedHolding line(
            BigDecimal quantity, BigDecimal averageCost, BigDecimal price, BigDecimal previousPrice) {
        Account account = new Account(UUID.randomUUID(), "Sample Broker", AccountType.PEA, "Sample Bank");
        Instrument instrument =
                new Instrument(INSTRUMENT_ID, "Test", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "TEST.PA", null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), INSTRUMENT_ID, quantity, averageCost);
        return new ValuedHolding(
                holding, instrument, account, quote(price), previousPrice == null ? null : quote(previousPrice));
    }

    private static Quote quote(BigDecimal price) {
        return new Quote(INSTRUMENT_ID, LocalDate.of(2026, 8, 21), price, "EUR", PriceSource.YAHOO, Instant.now());
    }
}
