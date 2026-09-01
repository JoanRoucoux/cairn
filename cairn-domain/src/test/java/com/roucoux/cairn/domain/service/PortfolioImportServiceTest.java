package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioImportServiceTest {

    private final List<Account> accounts = new ArrayList<>();
    private final List<Instrument> instruments = new ArrayList<>();
    private final List<Holding> holdings = new ArrayList<>();

    @Test
    void createsTheAccountTheInstrumentAndTheHoldingWhenNothingExistsYet() {
        PortfolioImportService service = serviceResolvingTo(aCandidate());

        ImportReport report = service.importPortfolio(List.of(aRow(new BigDecimal("100"), new BigDecimal("20"))));

        assertThat(report.accountsCreated()).isEqualTo(1);
        assertThat(report.instrumentsCreated()).isEqualTo(1);
        assertThat(report.holdingsCreated()).isEqualTo(1);
        assertThat(report.holdingsUpdated()).isZero();
        assertThat(holdings)
                .singleElement()
                .satisfies(holding -> assertThat(holding.quantity()).isEqualByComparingTo("100"));
    }

    @Test
    void updatesTheHoldingInPlaceWhenTheSameFileIsReplayed() {
        PortfolioImportService service = serviceResolvingTo(aCandidate());
        service.importPortfolio(List.of(aRow(new BigDecimal("100"), new BigDecimal("20"))));

        ImportReport report = service.importPortfolio(List.of(aRow(new BigDecimal("120"), new BigDecimal("21"))));

        assertThat(report.holdingsCreated()).isZero();
        assertThat(report.holdingsUpdated()).isEqualTo(1);
        assertThat(report.accountsCreated()).isZero();
        assertThat(report.instrumentsCreated()).isZero();
        assertThat(holdings)
                .singleElement()
                .satisfies(holding -> assertThat(holding.quantity()).isEqualByComparingTo("120"));
    }

    @Test
    void reportsEveryInvalidRowAtOnceAndWritesNothing() {
        PortfolioImportService service =
                serviceResolving(query -> "LU0000000001".equals(query) ? List.of(aCandidate()) : List.of());

        assertThatThrownBy(() -> service.importPortfolio(List.of(
                        aRow(BigDecimal.ZERO, new BigDecimal("20")), rowFor("UNKNOWN-TICKER", new BigDecimal("5")))))
                .isInstanceOf(PortfolioImportRejectedException.class)
                .asInstanceOf(type(PortfolioImportRejectedException.class))
                .extracting(PortfolioImportRejectedException::errors)
                .asInstanceOf(list(ImportError.class))
                .extracting(ImportError::rowIndex)
                .containsExactly(0, 1);

        assertThat(accounts).isEmpty();
        assertThat(instruments).isEmpty();
        assertThat(holdings).isEmpty();
    }

    private PortfolioImportService serviceResolvingTo(InstrumentCandidate... candidates) {
        return serviceResolving(query -> List.of(candidates));
    }

    private PortfolioImportService serviceResolving(ResolveInstrumentUseCase resolve) {
        LoadAccountsPort loadAccounts = new LoadAccountsPort() {
            @Override
            public List<Account> findAll() {
                return List.copyOf(accounts);
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return accounts.stream()
                        .filter(account -> account.id().equals(id))
                        .findFirst();
            }
        };
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.copyOf(instruments);
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return instruments.stream()
                        .filter(instrument -> instrument.id().equals(id))
                        .findFirst();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.copyOf(instruments);
            }
        };
        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
            @Override
            public List<Holding> findAll() {
                return List.copyOf(holdings);
            }

            @Override
            public Optional<Holding> findById(UUID id) {
                return holdings.stream()
                        .filter(holding -> holding.id().equals(id))
                        .findFirst();
            }

            @Override
            public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
                return holdings.stream()
                        .filter(holding -> holding.accountId().equals(accountId)
                                && holding.instrumentId().equals(instrumentId))
                        .findFirst();
            }
        };

        return new PortfolioImportService(
                loadAccounts,
                account -> {
                    accounts.add(account);
                    return account;
                },
                loadInstruments,
                instrument -> {
                    instruments.add(instrument);
                    return instrument;
                },
                resolve,
                loadHoldings,
                holding -> {
                    holdings.removeIf(existing -> existing.id().equals(holding.id()));
                    holdings.add(holding);
                    return holding;
                });
    }

    private static ImportRow aRow(BigDecimal quantity, BigDecimal averageCost) {
        return new ImportRow(
                "Sample Broker",
                AccountType.PEA,
                "Sample Bank",
                "Global Growth Tracker",
                "LU0000000001",
                quantity,
                averageCost);
    }

    private static ImportRow rowFor(String isinOrTicker, BigDecimal quantity) {
        return new ImportRow(
                "Sample Broker", AccountType.PEA, "Sample Bank", "Something Else", isinOrTicker, quantity, null);
    }

    private static InstrumentCandidate aCandidate() {
        return new InstrumentCandidate(
                "Global Growth Tracker", PriceSource.YAHOO, "GGT.PA", AssetClass.ETF, new BigDecimal("22"));
    }
}
