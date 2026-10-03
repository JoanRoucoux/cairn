package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportErrorCode;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T08:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

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
        assertThat(instruments)
                .singleElement()
                .satisfies(instrument -> assertThat(instrument.symbol()).isEqualTo("GGT"));
    }

    @Test
    void picksTheFirstEuroListingAndRecordsItsCurrency() {
        PortfolioImportService service = serviceResolvingTo(
                aCandidateIn("GGT.L", "USD"), aCandidateIn("GGT.DE", "EUR"), aCandidateIn("GGT.AS", "EUR"));

        service.importPortfolio(List.of(aRow(new BigDecimal("100"), new BigDecimal("20"))));

        assertThat(instruments).singleElement().satisfies(instrument -> {
            assertThat(instrument.sourceRef()).isEqualTo("GGT.DE");
            assertThat(instrument.currency()).isEqualTo("EUR");
        });
    }

    @Test
    void fallsBackToAnUnknownCurrencyListingWhenNoneIsEuro() {
        PortfolioImportService service = serviceResolvingTo(aCandidateIn("GGT.L", "USD"), aCandidateIn("GGT.X", null));

        service.importPortfolio(List.of(aRow(new BigDecimal("100"), new BigDecimal("20"))));

        assertThat(instruments).singleElement().satisfies(instrument -> {
            assertThat(instrument.sourceRef()).isEqualTo("GGT.X");
            assertThat(instrument.currency()).isEqualTo("EUR");
        });
    }

    @Test
    void rejectsTheRowWhenEveryListingIsInAnotherCurrency() {
        PortfolioImportService service =
                serviceResolvingTo(aCandidateIn("GGT.L", "USD"), aCandidateIn("GGT.SW", "CHF"));

        assertThatThrownBy(() -> service.importPortfolio(List.of(aRow(new BigDecimal("100"), new BigDecimal("20")))))
                .isInstanceOf(PortfolioImportRejectedException.class)
                .asInstanceOf(type(PortfolioImportRejectedException.class))
                .extracting(PortfolioImportRejectedException::errors)
                .asInstanceOf(list(ImportError.class))
                .extracting(ImportError::rowIndex, ImportError::code)
                .containsExactly(tuple(0, ImportErrorCode.UNRESOLVED_INSTRUMENT));
        assertThat(instruments).isEmpty();
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
        PortfolioImportService service = serviceResolving(query -> {
            if ("LU0000000001".equals(query)) {
                return List.of(aCandidate());
            }
            throw new UnknownInstrumentException(query);
        });

        assertThatThrownBy(() -> service.importPortfolio(List.of(
                        aRow(BigDecimal.ZERO, new BigDecimal("20")), rowFor("UNKNOWN-TICKER", new BigDecimal("5")))))
                .isInstanceOf(PortfolioImportRejectedException.class)
                .asInstanceOf(type(PortfolioImportRejectedException.class))
                .extracting(PortfolioImportRejectedException::errors)
                .asInstanceOf(list(ImportError.class))
                .extracting(ImportError::rowIndex, ImportError::code, ImportError::value)
                .containsExactly(
                        tuple(0, ImportErrorCode.ZERO_QUANTITY, null),
                        tuple(1, ImportErrorCode.UNRESOLVED_INSTRUMENT, "UNKNOWN-TICKER"));

        assertThat(accounts).isEmpty();
        assertThat(instruments).isEmpty();
        assertThat(holdings).isEmpty();
    }

    @Test
    void reportsALineOtherThanTheEuroCashBalanceOnASavingsAccountAsARowErrorAndWritesNothing() {
        PortfolioImportService service = serviceResolvingTo(aCandidate());
        ImportRow savingsLine = new ImportRow(
                "Livret A",
                AccountType.SAVINGS,
                "Woodgrove Bank",
                "Global Growth Tracker",
                "LU0000000001",
                BigDecimal.TEN,
                null);

        assertThatThrownBy(() -> service.importPortfolio(List.of(aRow(BigDecimal.ZERO, null), savingsLine)))
                .isInstanceOf(PortfolioImportRejectedException.class)
                .asInstanceOf(type(PortfolioImportRejectedException.class))
                .extracting(PortfolioImportRejectedException::errors)
                .asInstanceOf(list(ImportError.class))
                .extracting(ImportError::rowIndex, ImportError::code, ImportError::value)
                .containsExactly(
                        tuple(0, ImportErrorCode.ZERO_QUANTITY, null),
                        tuple(1, ImportErrorCode.SAVINGS_ACCOUNT_LINE, "LU0000000001"));

        assertThat(accounts).isEmpty();
        assertThat(instruments).isEmpty();
        assertThat(holdings).isEmpty();
    }

    @Test
    void theTypeOfAnExistingAccountWinsOverTheRowsWhenRefusingASavingsLine() {
        accounts.add(new Account(UUID.randomUUID(), "Livret A", AccountType.SAVINGS, "Woodgrove Bank"));
        PortfolioImportService service = serviceResolvingTo(aCandidate());
        ImportRow row = new ImportRow(
                "Livret A",
                AccountType.PEA,
                "Woodgrove Bank",
                "Global Growth Tracker",
                "LU0000000001",
                BigDecimal.TEN,
                null);

        assertThatThrownBy(() -> service.importPortfolio(List.of(row)))
                .isInstanceOf(PortfolioImportRejectedException.class);
        assertThat(holdings).isEmpty();
    }

    @Test
    void acceptsTheEuroCashBalanceOnASavingsAccountWhenTheEurosInstrumentExists() {
        instruments.add(new Instrument(
                UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null));
        PortfolioImportService service = serviceResolvingTo(aCandidate());
        ImportRow row = new ImportRow(
                "Livret A", AccountType.SAVINGS, "Woodgrove Bank", "Euros", "EUR", new BigDecimal("500"), null);

        ImportReport report = service.importPortfolio(List.of(row));

        assertThat(report.holdingsCreated()).isEqualTo(1);
        assertThat(report.instrumentsCreated()).isZero();
        assertThat(holdings).singleElement().satisfies(h -> {
            assertThat(h.quantity()).isEqualByComparingTo("500");
            assertThat(h.updatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void createsTheEuroCashInstrumentWhenASavingsBalanceIsImportedBeforeAnyExists() {
        PortfolioImportService service = serviceResolving(query -> {
            throw new UnknownInstrumentException(query);
        });
        ImportRow row = new ImportRow(
                "Livret A", AccountType.SAVINGS, "Woodgrove Bank", "Euros", "EUR", new BigDecimal("500"), null);

        ImportReport report = service.importPortfolio(List.of(row));

        assertThat(report.instrumentsCreated()).isEqualTo(1);
        assertThat(instruments).singleElement().satisfies(instrument -> {
            assertThat(instrument.isEurCash()).isTrue();
            assertThat(instrument.name()).isEqualTo("Euros");
        });
        assertThat(holdings)
                .singleElement()
                .satisfies(h -> assertThat(h.quantity()).isEqualByComparingTo("500"));
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

            @Override
            public List<Holding> findByInstrument(UUID instrumentId) {
                return holdings.stream()
                        .filter(holding -> holding.instrumentId().equals(instrumentId))
                        .toList();
            }

            @Override
            public List<Holding> findByAccount(UUID accountId) {
                return holdings.stream()
                        .filter(holding -> holding.accountId().equals(accountId))
                        .toList();
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
                },
                CLOCK);
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
                "Global Growth Tracker",
                PriceSource.YAHOO,
                "GGT.PA",
                AssetClass.ETF,
                "Paris",
                null,
                "GGT",
                new BigDecimal("22"),
                "EUR");
    }

    private static InstrumentCandidate aCandidateIn(String ref, String currency) {
        return new InstrumentCandidate(
                "Global Growth Tracker",
                PriceSource.YAHOO,
                ref,
                AssetClass.ETF,
                "Exchange",
                null,
                "GGT",
                new BigDecimal("22"),
                currency);
    }
}
