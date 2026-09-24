package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldingValuationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T13:57:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Account ACCOUNT = new Account(UUID.randomUUID(), "Fortuneo", AccountType.SAVINGS, "Fortuneo");
    private static final Instrument EUROS =
            new Instrument(UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);

    @Test
    void valuesManualCashAtParWithoutAnyStoredQuote() {
        ValuedHolding line = service(EUROS, List.of())
                .value(cash(new BigDecimal("20000"), null))
                .orElseThrow();

        assertThat(line.marketValue()).contains(new Money(new BigDecimal("20000"), "EUR"));
        assertThat(line.quote().orElseThrow().asOf()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(line.isStale(CLOCK)).isFalse();
    }

    @Test
    void cashNeverMovesInADay() {
        ValuedHolding line = service(EUROS, List.of())
                .value(cash(new BigDecimal("732.40"), null))
                .orElseThrow();

        assertThat(line.dayChange().orElseThrow().amount()).isEqualByComparingTo("0");
    }

    @Test
    void cashCarriesNoGainEvenWithoutACostBasis() {
        ValuedHolding line =
                service(EUROS, List.of()).value(cash(BigDecimal.TEN, null)).orElseThrow();

        assertThat(line.unrealizedGain().orElseThrow().amount()).isEqualByComparingTo("0");
    }

    @Test
    void cashRatiosAreZeroWithoutACostBasis() {
        ValuedHolding line =
                service(EUROS, List.of()).value(cash(BigDecimal.TEN, null)).orElseThrow();

        assertThat(line.unrealizedGainRatio().orElseThrow()).isEqualByComparingTo("0");
        assertThat(line.dayChangeRatio().orElseThrow()).isEqualByComparingTo("0");
    }

    @Test
    void ignoresAStoredQuoteForManualCash() {
        Quote stale = new Quote(
                EUROS.id(),
                LocalDate.of(2020, 1, 1),
                new BigDecimal("0.5"),
                "EUR",
                PriceSource.MANUAL,
                Instant.parse("2020-01-01T00:00:00Z"));

        ValuedHolding line =
                service(EUROS, List.of(stale)).value(cash(BigDecimal.TEN, null)).orElseThrow();

        assertThat(line.marketValue().orElseThrow().amount()).isEqualByComparingTo("10");
    }

    private static Holding cash(BigDecimal amount, BigDecimal averageCost) {
        return new Holding(UUID.randomUUID(), ACCOUNT.id(), EUROS.id(), amount, averageCost);
    }

    private static HoldingValuationService service(Instrument instrument, List<Quote> quotes) {
        LoadInstrumentsPort instruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of(instrument);
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return instrument.id().equals(id) ? Optional.of(instrument) : Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };
        LoadAccountsPort accounts = new LoadAccountsPort() {
            @Override
            public List<Account> findAll() {
                return List.of(ACCOUNT);
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return ACCOUNT.id().equals(id) ? Optional.of(ACCOUNT) : Optional.empty();
            }
        };
        LoadQuotesPort loadQuotes = new LoadQuotesPort() {
            @Override
            public Optional<Quote> findLatest(UUID instrumentId) {
                return quotes.stream()
                        .filter(q -> q.instrumentId().equals(instrumentId))
                        .findFirst();
            }

            @Override
            public Optional<Quote> findPrevious(UUID instrumentId, LocalDate before) {
                return Optional.empty();
            }

            @Override
            public List<Quote> findBetween(UUID instrumentId, LocalDate from, LocalDate to) {
                return List.of();
            }

            @Override
            public Map<UUID, List<Quote>> findBetweenForAll(Set<UUID> instrumentIds, LocalDate from, LocalDate to) {
                return Map.of();
            }

            @Override
            public Map<UUID, Quote> findLatestOnOrBefore(Set<UUID> instrumentIds, LocalDate day) {
                return Map.of();
            }

            @Override
            public Map<UUID, LocalDate> findFirstQuoteDates(Set<UUID> instrumentIds) {
                return Map.of();
            }
        };
        return new HoldingValuationService(instruments, accounts, loadQuotes, CLOCK);
    }
}
