package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.CashOnlyAccount;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.Summary;
import com.roucoux.cairn.domain.model.SummaryPeriod;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.out.SendNotificationPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SummaryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T23:30:00Z"), ZoneOffset.UTC);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    private static final Performance D1_PERFORMANCE = new Performance(
            Optional.of(PerformanceRange.D1),
            LocalDate.of(2026, 9, 22),
            TODAY,
            false,
            Optional.empty(),
            Money.eur(new BigDecimal("298889")),
            Money.eur(new BigDecimal("2431")),
            Optional.of(new BigDecimal("0.0082")),
            List.of());

    private static final Instrument EUROS =
            new Instrument(UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);
    private static final Instrument ETF = new Instrument(
            UUID.randomUUID(), "Sample ETF", "FR0011550185", "EUR", AssetClass.ETF, PriceSource.YAHOO, "ESE.PA", null);

    private static final Account PEA =
            new Account(UUID.randomUUID(), "Northwind PEA", AccountType.PEA, "Northwind Bank");
    private static final Account LIVRET_A = new Account(UUID.randomUUID(), "Livret A", AccountType.SAVINGS, "Bank");
    private static final Account LDDS = new Account(UUID.randomUUID(), "LDDS", AccountType.SAVINGS, "Bank");

    @Test
    void readsD1PerformanceAndSendsItOnceForTodayInParis() {
        List<PerformanceRange> requestedRanges = new ArrayList<>();
        GetPerformanceUseCase getPerformance = new GetPerformanceUseCase() {
            @Override
            public Performance performance(PerformanceRange range) {
                requestedRanges.add(range);
                return D1_PERFORMANCE;
            }

            @Override
            public Performance performanceSince(LocalDate baseline) {
                throw new AssertionError("not expected");
            }
        };
        List<Summary> sent = new ArrayList<>();

        new SummaryService(getPerformance, () -> portfolioOf(List.of()), sent::add, CLOCK, PARIS).send();

        assertThat(requestedRanges).containsExactly(PerformanceRange.D1);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().period()).isEqualTo(SummaryPeriod.DAY);
        assertThat(sent.getFirst().first()).isEqualTo(TODAY);
        assertThat(sent.getFirst().last()).isEqualTo(TODAY);
        assertThat(sent.getFirst().performance()).isSameAs(D1_PERFORMANCE);
        assertThat(sent.getFirst().cashOnlyAccounts()).isEmpty();
    }

    @Test
    void aWeeklyRecapReadsPerformanceSinceTheDayBeforeMondayAndCoversMondayToFriday() {
        assertPeriodRecap(
                SummaryPeriod.WEEK,
                "2026-10-17T07:00:00Z",
                LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 16));
    }

    @Test
    void aMonthlyRecapReadsPerformanceSinceTheLastDayOfTheMonthBefore() {
        assertPeriodRecap(
                SummaryPeriod.MONTH,
                "2026-11-01T08:00:00Z",
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31));
    }

    @Test
    void aYearlyRecapReadsPerformanceSinceTheLastDayOfTheYearBefore() {
        assertPeriodRecap(
                SummaryPeriod.YEAR,
                "2027-01-01T08:05:00Z",
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
    }

    private static void assertPeriodRecap(
            SummaryPeriod period, String instant, LocalDate baseline, LocalDate first, LocalDate last) {
        List<LocalDate> baselines = new ArrayList<>();
        GetPerformanceUseCase getPerformance = new GetPerformanceUseCase() {
            @Override
            public Performance performance(PerformanceRange range) {
                throw new AssertionError("not expected");
            }

            @Override
            public Performance performanceSince(LocalDate requested) {
                baselines.add(requested);
                return D1_PERFORMANCE;
            }
        };
        List<Summary> sent = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);

        new SummaryService(getPerformance, () -> portfolioOf(List.of()), sent::add, clock, PARIS).send(period);

        assertThat(baselines).containsExactly(baseline);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().period()).isEqualTo(period);
        assertThat(sent.getFirst().first()).isEqualTo(first);
        assertThat(sent.getFirst().last()).isEqualTo(last);
        assertThat(sent.getFirst().performance()).isSameAs(D1_PERFORMANCE);
    }

    @Test
    void aRecapWhoseQuoteHistoryStartsAfterThePeriodIsNotSent() {
        Performance noHistory = new Performance(
                Optional.empty(),
                LocalDate.of(2026, 10, 17),
                LocalDate.of(2026, 10, 17),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("20010")),
                Money.zeroEur(),
                Optional.empty(),
                List.of());
        GetPerformanceUseCase getPerformance = new GetPerformanceUseCase() {
            @Override
            public Performance performance(PerformanceRange range) {
                throw new AssertionError("not expected");
            }

            @Override
            public Performance performanceSince(LocalDate baseline) {
                return noHistory;
            }
        };
        List<Summary> sent = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-10-17T07:00:00Z"), ZoneOffset.UTC);

        new SummaryService(getPerformance, () -> portfolioOf(List.of()), sent::add, clock, PARIS)
                .send(SummaryPeriod.WEEK);

        assertThat(sent).isEmpty();
    }

    private static GetPerformanceUseCase d1Only() {
        return new GetPerformanceUseCase() {
            @Override
            public Performance performance(PerformanceRange range) {
                return D1_PERFORMANCE;
            }

            @Override
            public Performance performanceSince(LocalDate baseline) {
                throw new AssertionError("not expected");
            }
        };
    }

    @Test
    void listsEachAccountOfAnEnvelopeHoldingOnlyCashAtParLargestFirst() {
        GetPortfolioUseCase getPortfolio = () -> portfolioOf(List.of(
                line(PEA, ETF, "100", Optional.of(quote(ETF, "25"))),
                line(PEA, EUROS, "732.40", Optional.of(par())),
                line(LDDS, EUROS, "10", Optional.of(par())),
                line(LIVRET_A, EUROS, "20000", Optional.of(par()))));
        List<Summary> sent = new ArrayList<>();
        SendNotificationPort sendNotification = sent::add;

        new SummaryService(d1Only(), getPortfolio, sendNotification, CLOCK, PARIS).send();

        assertThat(sent.getFirst().cashOnlyAccounts())
                .containsExactly(
                        new CashOnlyAccount("Livret A", AccountType.SAVINGS, Money.eur(new BigDecimal("20000"))),
                        new CashOnlyAccount("LDDS", AccountType.SAVINGS, Money.eur(new BigDecimal("10"))));
    }

    @Test
    void anEnvelopeWithOneQuotedLineIsNotCashOnlyEvenInAnotherAccount() {
        Account otherSavings = new Account(UUID.randomUUID(), "Assurance", AccountType.SAVINGS, "Bank");
        GetPortfolioUseCase getPortfolio = () -> portfolioOf(List.of(
                line(LIVRET_A, EUROS, "20000", Optional.of(par())),
                line(otherSavings, ETF, "10", Optional.of(quote(ETF, "25")))));
        List<Summary> sent = new ArrayList<>();

        new SummaryService(d1Only(), getPortfolio, sent::add, CLOCK, PARIS).send();

        assertThat(sent.getFirst().cashOnlyAccounts()).isEmpty();
    }

    @Test
    void aLineWithNoQuoteYetIsIgnored() {
        GetPortfolioUseCase getPortfolio = () -> portfolioOf(
                List.of(line(LIVRET_A, EUROS, "20000", Optional.of(par())), line(LDDS, ETF, "10", Optional.empty())));
        List<Summary> sent = new ArrayList<>();

        new SummaryService(d1Only(), getPortfolio, sent::add, CLOCK, PARIS).send();

        assertThat(sent.getFirst().cashOnlyAccounts())
                .containsExactly(
                        new CashOnlyAccount("Livret A", AccountType.SAVINGS, Money.eur(new BigDecimal("20000"))));
    }

    @Test
    void aLineQuotedInAnotherCurrencyIsIgnored() {
        Instrument usdEtf = new Instrument(
                UUID.randomUUID(), "US ETF", null, "USD", AssetClass.ETF, PriceSource.YAHOO, "SPY", null);
        Quote usdQuote =
                new Quote(usdEtf.id(), TODAY, new BigDecimal("500"), "USD", PriceSource.YAHOO, CLOCK.instant());
        GetPortfolioUseCase getPortfolio = () -> portfolioOf(List.of(
                line(LIVRET_A, EUROS, "20000", Optional.of(par())),
                line(LIVRET_A, usdEtf, "10", Optional.of(usdQuote))));
        List<Summary> sent = new ArrayList<>();

        new SummaryService(d1Only(), getPortfolio, sent::add, CLOCK, PARIS).send();

        assertThat(sent.getFirst().cashOnlyAccounts())
                .containsExactly(
                        new CashOnlyAccount("Livret A", AccountType.SAVINGS, Money.eur(new BigDecimal("20000"))));
    }

    @Test
    void anEnvelopeWhoseOnlyPricedLineIsInAnotherCurrencyReadsAsCashOnly() {
        Instrument usdEtf = new Instrument(
                UUID.randomUUID(), "US ETF", null, "USD", AssetClass.ETF, PriceSource.YAHOO, "SPY", null);
        Quote usdQuote =
                new Quote(usdEtf.id(), TODAY, new BigDecimal("500"), "USD", PriceSource.YAHOO, CLOCK.instant());
        GetPortfolioUseCase getPortfolio = () -> portfolioOf(
                List.of(line(PEA, EUROS, "300", Optional.of(par())), line(PEA, usdEtf, "10", Optional.of(usdQuote))));
        List<Summary> sent = new ArrayList<>();

        new SummaryService(d1Only(), getPortfolio, sent::add, CLOCK, PARIS).send();

        assertThat(sent.getFirst().cashOnlyAccounts())
                .containsExactly(
                        new CashOnlyAccount("Northwind PEA", AccountType.PEA, Money.eur(new BigDecimal("300"))));
    }

    private static ValuedHolding line(Account account, Instrument instrument, String quantity, Optional<Quote> quote) {
        Holding holding = new Holding(
                UUID.randomUUID(), account.id(), instrument.id(), new BigDecimal(quantity), null, Instant.EPOCH);
        return new ValuedHolding(holding, instrument, account, quote, Optional.empty());
    }

    private static Quote par() {
        return Quote.atPar(EUROS.id(), "EUR", TODAY, CLOCK.instant());
    }

    private static Quote quote(Instrument instrument, String price) {
        return new Quote(instrument.id(), TODAY, new BigDecimal(price), "EUR", PriceSource.YAHOO, CLOCK.instant());
    }

    private static Portfolio portfolioOf(List<ValuedHolding> holdings) {
        return new Portfolio(
                Money.zeroEur(), Money.zeroEur(), Optional.empty(), List.of(), List.of(), holdings, 0, 0, 0);
    }
}
