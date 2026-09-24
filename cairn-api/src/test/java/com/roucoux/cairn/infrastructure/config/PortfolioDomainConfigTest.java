package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.service.HoldingValuationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioDomainConfigTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK =
            Clock.fixed(LocalDate.of(2026, 9, 24).atTime(20, 0).atZone(ZONE).toInstant(), ZONE);
    private static final Account ACCOUNT = new Account(UUID.randomUUID(), "Fortuneo", AccountType.SAVINGS, "Fortuneo");
    private static final Instrument EUROS =
            new Instrument(UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);

    @Test
    void wiresTheZoneThroughSoTheRangeIsAnchoredOnItsOwnDay() {
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
        };
        Holding cash = new Holding(UUID.randomUUID(), ACCOUNT.id(), EUROS.id(), new BigDecimal("20000"), null);
        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
            @Override
            public List<Holding> findAll() {
                return List.of(cash);
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
        ValueHoldingUseCase valueHolding =
                new HoldingValuationService(loadInstruments, loadAccounts, loadQuotes, CLOCK);

        GetPerformanceUseCase useCase =
                new PortfolioDomainConfig().getPerformanceUseCase(loadHoldings, valueHolding, loadQuotes, CLOCK, ZONE);
        Performance performance = useCase.performance(PerformanceRange.D1);

        assertThat(performance.to()).isEqualTo(LocalDate.now(CLOCK.withZone(ZONE)));
        assertThat(performance.total().amount()).isEqualByComparingTo("20000");
    }

    @Test
    void wiresTheZoneBeanFromTheConfiguredProperty() {
        ZoneId zone = new PortfolioDomainConfig().zone("Europe/Paris");

        assertThat(zone).isEqualTo(ZoneId.of("Europe/Paris"));
    }
}
