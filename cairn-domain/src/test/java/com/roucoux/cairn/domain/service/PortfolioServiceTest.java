package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.roucoux.cairn.domain.exception.business.NonEurHoldingException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.Allocation;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
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

class PortfolioServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 8, 21)
                    .atTime(20, 0)
                    .atZone(ZoneId.of("Europe/Paris"))
                    .toInstant(),
            ZoneId.of("Europe/Paris"));

    @Test
    void totalsTheWholePortfolio() {
        Portfolio portfolio = serviceWithSamplePortfolio().get();

        assertThat(portfolio.total().amount()).isEqualByComparingTo("25950");
    }

    @Test
    void splitsByAssetClassWithSharesSummingToOne() {
        Portfolio portfolio = serviceWithSamplePortfolio().get();

        assertThat(portfolio.byAssetClass())
                .extracting(Allocation::label)
                .contains("FUND", "ETF", "EQUITY", "CASH", "CRYPTO");
        assertThat(portfolio.byAssetClass().stream().map(Allocation::share).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isCloseTo(BigDecimal.ONE, within(new BigDecimal("0.0001")));
    }

    @Test
    void leavesTheUnrealizedGainEmptyWhenNoHoldingCarriesACostBasis() {
        PortfolioService service =
                serviceWith(List.of(holding(new BigDecimal("296"), null, AssetClass.EQUITY, new BigDecimal("76.40"))));

        assertThat(service.get().unrealizedGain()).isEmpty();
    }

    @Test
    void leavesTheUnrealizedGainEmptyWhenOnlySomeHoldingsCarryACostBasis() {
        PortfolioService service = serviceWith(List.of(
                holding(new BigDecimal("10"), new BigDecimal("50.00"), AssetClass.EQUITY, new BigDecimal("60.00")),
                holding(new BigDecimal("296"), null, AssetClass.EQUITY, new BigDecimal("76.40"))));

        assertThat(service.get().unrealizedGain()).isEmpty();
    }

    @Test
    void countsTheStaleLines() {
        PortfolioService service = serviceWith(List.of(
                holdingQuotedOn(LocalDate.of(2026, 8, 20), AssetClass.EQUITY),
                holdingQuotedOn(LocalDate.of(2026, 8, 10), AssetClass.EQUITY)));

        assertThat(service.get().staleCount()).isEqualTo(1);
    }

    @Test
    void rejectsANonEurHoldingInsteadOfCrashingTheWholePortfolio() {
        Line eurLine = holding(new BigDecimal("10"), null, AssetClass.EQUITY, new BigDecimal("50.00"));
        Line usdLine = nonEurLine("US0000000001", "USD");
        PortfolioService service = serviceWith(List.of(eurLine, usdLine));

        assertThatThrownBy(service::get)
                .isInstanceOf(NonEurHoldingException.class)
                .hasMessageContaining("US0000000001")
                .hasMessageContaining("USD");
    }

    private static Line nonEurLine(String isin, String currency) {
        UUID instrumentId = UUID.randomUUID();
        Account account = new Account(UUID.randomUUID(), "Test", AccountType.CTO, "Test");
        Instrument instrument = new Instrument(
                instrumentId, "Test", isin, currency, AssetClass.EQUITY, PriceSource.YAHOO, "TEST", null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, BigDecimal.ONE, null);
        Quote quote = new Quote(
                instrumentId, LocalDate.of(2026, 8, 21), BigDecimal.TEN, currency, PriceSource.YAHOO, CLOCK.instant());
        return new Line(holding, instrument, account, quote);
    }

    private record Line(Holding holding, Instrument instrument, Account account, Quote quote) {}

    private static Line holding(BigDecimal quantity, BigDecimal averageCost, AssetClass assetClass, BigDecimal price) {
        return line(quantity, averageCost, assetClass, price, LocalDate.of(2026, 8, 21));
    }

    private static Line holdingQuotedOn(LocalDate asOf, AssetClass assetClass) {
        return line(BigDecimal.ONE, null, assetClass, BigDecimal.TEN, asOf);
    }

    private static Line line(
            BigDecimal quantity, BigDecimal averageCost, AssetClass assetClass, BigDecimal price, LocalDate asOf) {
        UUID instrumentId = UUID.randomUUID();
        Account account = new Account(UUID.randomUUID(), "Test", AccountType.CTO, "Test");
        PriceSource source = assetClass == AssetClass.CASH ? PriceSource.MANUAL : PriceSource.YAHOO;
        String ref = source == PriceSource.MANUAL ? null : "TEST.PA";
        Instrument instrument = new Instrument(instrumentId, "Test", null, "EUR", assetClass, source, ref, null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, quantity, averageCost);
        Quote quote = new Quote(instrumentId, asOf, price, "EUR", source, CLOCK.instant());
        return new Line(holding, instrument, account, quote);
    }

    private static PortfolioService serviceWith(List<Line> lines) {
        List<Holding> holdings = lines.stream().map(Line::holding).toList();
        List<Instrument> instruments = lines.stream().map(Line::instrument).toList();
        List<Account> accounts = lines.stream().map(Line::account).distinct().toList();
        List<Quote> quotes = lines.stream().map(Line::quote).toList();

        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
            @Override
            public List<Holding> findAll() {
                return holdings;
            }

            @Override
            public Optional<Holding> findById(UUID id) {
                return holdings.stream().filter(h -> h.id().equals(id)).findFirst();
            }

            @Override
            public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
                return holdings.stream()
                        .filter(h -> h.accountId().equals(accountId)
                                && h.instrumentId().equals(instrumentId))
                        .findFirst();
            }
        };
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return instruments;
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return instruments.stream().filter(i -> i.id().equals(id)).findFirst();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };
        LoadAccountsPort loadAccounts = new LoadAccountsPort() {
            @Override
            public List<Account> findAll() {
                return accounts;
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return accounts.stream().filter(a -> a.id().equals(id)).findFirst();
            }
        };
        LoadQuotesPort loadQuotes = new LoadQuotesPort() {
            @Override
            public Optional<Quote> findLatest(UUID instrumentId) {
                return quotes.stream()
                        .filter(quote -> quote.instrumentId().equals(instrumentId))
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
        };

        return new PortfolioService(
                loadHoldings, new HoldingValuationService(loadInstruments, loadAccounts, loadQuotes), CLOCK);
    }

    private static PortfolioService serviceWithSamplePortfolio() {
        Account broker1 = new Account(UUID.randomUUID(), "Sample Broker One", AccountType.PEA, "Sample Bank");
        Account broker2 = new Account(UUID.randomUUID(), "Sample Broker Two", AccountType.CTO, "Sample Securities");
        Account exchange = new Account(UUID.randomUUID(), "Sample Exchange", AccountType.CRYPTO, "Sample Exchange");
        Account pension = new Account(UUID.randomUUID(), "Sample Pension", AccountType.PER, "Sample Insurer");
        Account employer =
                new Account(UUID.randomUUID(), "Sample Employer Plan", AccountType.PEE, "Sample Asset Manager");
        Account lifeInsurance =
                new Account(UUID.randomUUID(), "Sample Life Insurance", AccountType.LIFE_INSURANCE, "Sample Insurer");
        Account savings = new Account(UUID.randomUUID(), "Sample Savings", AccountType.SAVINGS, "Sample Bank");

        List<Line> lines = List.of(
                realLine(
                        broker1,
                        "Northwind Traders",
                        AssetClass.EQUITY,
                        "FR0000000001",
                        PriceSource.YAHOO,
                        "NWT.PA",
                        "10",
                        "50.00",
                        "55.00"),
                realLine(
                        broker1,
                        "Global Growth Tracker",
                        AssetClass.ETF,
                        "LU0000000001",
                        PriceSource.YAHOO,
                        "GGT.PA",
                        "100",
                        "20.00",
                        "22.00"),
                cashLine(broker1, "Liquidites PEA", "500"),
                realLine(
                        broker2,
                        "Acme Corp",
                        AssetClass.EQUITY,
                        "FR0000000002",
                        PriceSource.YAHOO,
                        "ACM.PA",
                        "50",
                        null,
                        "40.00"),
                cryptoLine(exchange, "Bitcoin", "bitcoin", "0.1", "50000"),
                cryptoLine(exchange, "Ethereum", "ethereum", "2", "3000"),
                fundLine(pension, "Balanced Fund", "FR0000000003", "BF.F", "50", "100.00"),
                fundLine(employer, "Employer Equity Fund", "QS0000000001", "EEF.F", "20", "50.00"),
                realLine(
                        lifeInsurance,
                        "World Index Acc",
                        AssetClass.ETF,
                        "LU0000000002",
                        PriceSource.YAHOO,
                        "WIA.PA",
                        "30",
                        "80.00",
                        "90.00"),
                cashLine(savings, "Savings Account", "1000"));

        return serviceWith(lines);
    }

    private static Line realLine(
            Account account,
            String name,
            AssetClass assetClass,
            String isin,
            PriceSource source,
            String sourceRef,
            String quantity,
            String averageCost,
            String price) {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument(instrumentId, name, isin, "EUR", assetClass, source, sourceRef, null);
        Holding holding = new Holding(
                UUID.randomUUID(),
                account.id(),
                instrumentId,
                new BigDecimal(quantity),
                averageCost == null ? null : new BigDecimal(averageCost));
        Quote quote = new Quote(
                instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal(price), "EUR", source, CLOCK.instant());
        return new Line(holding, instrument, account, quote);
    }

    private static Line cashLine(Account account, String name, String quantity) {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument =
                new Instrument(instrumentId, name, null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);
        Holding holding =
                new Holding(UUID.randomUUID(), account.id(), instrumentId, new BigDecimal(quantity), BigDecimal.ONE);
        Quote quote = new Quote(
                instrumentId, LocalDate.of(2026, 8, 21), BigDecimal.ONE, "EUR", PriceSource.MANUAL, CLOCK.instant());
        return new Line(holding, instrument, account, quote);
    }

    private static Line cryptoLine(Account account, String name, String sourceRef, String quantity, String price) {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument(
                instrumentId, name, null, "EUR", AssetClass.CRYPTO, PriceSource.COINGECKO, sourceRef, null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, new BigDecimal(quantity), null);
        Quote quote = new Quote(
                instrumentId,
                LocalDate.of(2026, 8, 21),
                new BigDecimal(price),
                "EUR",
                PriceSource.COINGECKO,
                CLOCK.instant());
        return new Line(holding, instrument, account, quote);
    }

    private static Line fundLine(
            Account account, String name, String isin, String sourceRef, String quantity, String price) {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument =
                new Instrument(instrumentId, name, isin, "EUR", AssetClass.FUND, PriceSource.YAHOO, sourceRef, null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, new BigDecimal(quantity), null);
        Quote quote = new Quote(
                instrumentId,
                LocalDate.of(2026, 8, 21),
                new BigDecimal(price),
                "EUR",
                PriceSource.YAHOO,
                CLOCK.instant());
        return new Line(holding, instrument, account, quote);
    }

    private static Line sgSiriusFundLine(Account account, String name, String isin, String quantity, String price) {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument =
                new Instrument(instrumentId, name, isin, "EUR", AssetClass.FUND, PriceSource.SG_SIRIUS, isin, null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, new BigDecimal(quantity), null);
        Quote quote = new Quote(
                instrumentId,
                LocalDate.of(2026, 8, 21),
                new BigDecimal(price),
                "EUR",
                PriceSource.SG_SIRIUS,
                CLOCK.instant());
        return new Line(holding, instrument, account, quote);
    }
}
