package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
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

class HoldingDomainConfigTest {

    private static final Instant NOW = Instant.parse("2026-09-23T13:57:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Account ACCOUNT = new Account(UUID.randomUUID(), "Fortuneo", AccountType.SAVINGS, "Fortuneo");
    private static final Instrument EUROS =
            new Instrument(UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);

    @Test
    void wiresTheClockThroughSoACashHoldingIsValuedAtParAsOfToday() {
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of(EUROS);
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return EUROS.id().equals(id) ? Optional.of(EUROS) : Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };
        LoadAccountsPort loadAccounts = new LoadAccountsPort() {
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
                return Optional.empty();
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
        Holding cash = new Holding(UUID.randomUUID(), ACCOUNT.id(), EUROS.id(), new BigDecimal("20000"), null);

        ValueHoldingUseCase useCase =
                new HoldingDomainConfig().valueHoldingUseCase(loadInstruments, loadAccounts, loadQuotes, CLOCK);
        ValuedHolding valued = useCase.value(cash).orElseThrow();

        assertThat(valued.marketValue().orElseThrow().amount()).isEqualByComparingTo("20000");
        assertThat(valued.quote().orElseThrow().asOf()).isEqualTo(LocalDate.now(CLOCK));
    }

    @Test
    void wiresTheCashBalanceServiceThroughSoItCreatesTheEurosInstrumentOnFirstUse() {
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of();
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };
        LoadAccountsPort loadAccounts = new LoadAccountsPort() {
            @Override
            public List<Account> findAll() {
                return List.of(ACCOUNT);
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return ACCOUNT.id().equals(id) ? Optional.of(ACCOUNT) : Optional.empty();
            }
        };
        com.roucoux.cairn.domain.port.out.SaveInstrumentPort saveInstrument = instrument -> instrument;
        com.roucoux.cairn.domain.port.out.LoadHoldingsPort loadHoldings =
                new com.roucoux.cairn.domain.port.out.LoadHoldingsPort() {
                    @Override
                    public List<Holding> findAll() {
                        return List.of();
                    }

                    @Override
                    public Optional<Holding> findById(UUID id) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
                        return Optional.empty();
                    }

                    @Override
                    public List<Holding> findByInstrument(UUID instrumentId) {
                        return List.of();
                    }
                };
        List<Holding> saved = new java.util.ArrayList<>();
        com.roucoux.cairn.domain.port.out.SaveHoldingPort saveHolding = holding -> {
            saved.add(holding);
            return holding;
        };
        com.roucoux.cairn.domain.port.out.DeleteHoldingPort deleteHolding = id -> {};

        com.roucoux.cairn.domain.port.in.SetCashBalanceUseCase useCase = new HoldingDomainConfig()
                .setCashBalanceUseCase(
                        loadAccounts, loadInstruments, saveInstrument, loadHoldings, saveHolding, deleteHolding);
        useCase.setCashBalance(ACCOUNT.id(), new BigDecimal("732.40"));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).quantity()).isEqualByComparingTo("732.40");
    }
}
