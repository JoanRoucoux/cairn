package com.roucoux.cairn.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldingRestMapperTest {

    private final HoldingRestMapper mapper = new HoldingRestMapper(Clock.systemUTC());
    private final Account savings = new Account(UUID.randomUUID(), "Livret A", AccountType.SAVINGS, "Woodgrove Bank");

    @Test
    void marksTheAccountEuroCashBalance() {
        Instrument euros = new Instrument(
                UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);

        assertThat(mapper.toResponse(valued(euros)).getAccountCash()).isTrue();
    }

    @Test
    void exposesTheInstrumentSymbol() {
        Instrument ether = new Instrument(
                UUID.randomUUID(),
                "Ethereum",
                null,
                "ETH",
                "EUR",
                AssetClass.CRYPTO,
                PriceSource.COINGECKO,
                "ethereum",
                null);

        assertThat(mapper.toResponse(valued(ether)).getSymbol()).isEqualTo("ETH");
    }

    @Test
    void doesNotMarkASavingsBooklet() {
        Instrument livretA = new Instrument(
                UUID.randomUUID(), "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);

        assertThat(mapper.toResponse(valued(livretA)).getAccountCash()).isFalse();
    }

    @Test
    void exposesWhenTheHoldingWasLastWritten() {
        Instrument euros = new Instrument(
                UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);
        Instant writtenAt = Instant.parse("2026-09-12T08:30:00Z");
        Holding holding = new Holding(
                UUID.randomUUID(), savings.id(), euros.id(), new BigDecimal("1500"), BigDecimal.ONE, writtenAt);

        assertThat(mapper.toResponse(holding).getUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-09-12T08:30:00Z"));
        assertThat(mapper.toResponse(new ValuedHolding(holding, euros, savings, Optional.empty(), Optional.empty()))
                        .getUpdatedAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-12T08:30:00Z"));
    }

    @Test
    void doesNotMarkAHoldingItCouldNotValue() {
        Holding holding = new Holding(
                UUID.randomUUID(), savings.id(), UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE, Instant.EPOCH);

        assertThat(mapper.toResponse(holding).getAccountCash()).isFalse();
    }

    @Test
    void exposesWhenTheLatestQuoteWasFetched() {
        Instrument share = new Instrument(
                UUID.randomUUID(),
                "Sample Bank Share",
                null,
                "EUR",
                AssetClass.EQUITY,
                PriceSource.YAHOO,
                "GLE.PA",
                null);

        assertThat(mapper.toResponse(valued(share, quoteFetchedAt(share, "2026-09-25T15:35:00Z")))
                        .getPriceFetchedAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-25T15:35:00Z"));
    }

    @Test
    void exposesWhenAManualQuoteWasEntered() {
        Instrument manual = new Instrument(
                UUID.randomUUID(), "Sample Fund", null, "EUR", AssetClass.FUND, PriceSource.MANUAL, null, null);

        assertThat(mapper.toResponse(valued(manual, quoteFetchedAt(manual, "2026-09-20T08:00:00Z")))
                        .getPriceFetchedAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-20T08:00:00Z"));
    }

    @Test
    void leavesTheFetchTimeAbsentForCashPricedAtPar() {
        Instrument livretA = new Instrument(
                UUID.randomUUID(), "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);

        assertThat(mapper.toResponse(valued(livretA, quoteFetchedAt(livretA, "2026-09-25T15:35:00Z")))
                        .getPriceFetchedAt())
                .isNull();
    }

    @Test
    void leavesTheFetchTimeAbsentWithoutAQuote() {
        Instrument share = new Instrument(
                UUID.randomUUID(),
                "Sample Bank Share",
                null,
                "EUR",
                AssetClass.EQUITY,
                PriceSource.YAHOO,
                "GLE.PA",
                null);

        assertThat(mapper.toResponse(valued(share)).getPriceFetchedAt()).isNull();
    }

    private Quote quoteFetchedAt(Instrument instrument, String fetchedAt) {
        return new Quote(
                instrument.id(),
                LocalDate.of(2026, 9, 25),
                BigDecimal.TEN,
                "EUR",
                instrument.priceSource(),
                Instant.parse(fetchedAt));
    }

    private ValuedHolding valued(Instrument instrument, Quote quote) {
        Holding holding = new Holding(
                UUID.randomUUID(),
                savings.id(),
                instrument.id(),
                new BigDecimal("1500"),
                BigDecimal.ONE,
                Instant.EPOCH);
        return new ValuedHolding(holding, instrument, savings, Optional.of(quote), Optional.empty());
    }

    private ValuedHolding valued(Instrument instrument) {
        Holding holding = new Holding(
                UUID.randomUUID(),
                savings.id(),
                instrument.id(),
                new BigDecimal("1500"),
                BigDecimal.ONE,
                Instant.EPOCH);
        return new ValuedHolding(holding, instrument, savings, Optional.empty(), Optional.empty());
    }
}
