package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.CashOnlyAccount;
import com.roucoux.cairn.domain.model.DailySummary;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
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

class DailySummaryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T23:30:00Z"), ZoneOffset.UTC);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    private static final Performance D1_PERFORMANCE = new Performance(
            PerformanceRange.D1,
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

    private static final Account PEA = new Account(UUID.randomUUID(), "Saxo Investor", AccountType.PEA, "Saxo");
    private static final Account LIVRET_A = new Account(UUID.randomUUID(), "Livret A", AccountType.SAVINGS, "Bank");
    private static final Account LDDS = new Account(UUID.randomUUID(), "LDDS", AccountType.SAVINGS, "Bank");

    @Test
    void readsD1PerformanceAndSendsItOnceForTodayInParis() {
        List<PerformanceRange> requestedRanges = new ArrayList<>();
        GetPerformanceUseCase getPerformance = range -> {
            requestedRanges.add(range);
            return D1_PERFORMANCE;
        };
        List<DailySummary> sent = new ArrayList<>();

        new DailySummaryService(getPerformance, () -> portfolioOf(List.of()), sent::add, CLOCK, PARIS).send();

        assertThat(requestedRanges).containsExactly(PerformanceRange.D1);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().date()).isEqualTo(TODAY);
        assertThat(sent.getFirst().performance()).isSameAs(D1_PERFORMANCE);
        assertThat(sent.getFirst().cashOnlyAccounts()).isEmpty();
    }

    @Test
    void listsEachAccountOfAnEnvelopeHoldingOnlyCashAtParLargestFirst() {
        GetPortfolioUseCase getPortfolio = () -> portfolioOf(List.of(
                line(PEA, ETF, "100", Optional.of(quote(ETF, "25"))),
                line(PEA, EUROS, "732.40", Optional.of(par())),
                line(LDDS, EUROS, "10", Optional.of(par())),
                line(LIVRET_A, EUROS, "20000", Optional.of(par()))));
        List<DailySummary> sent = new ArrayList<>();
        SendNotificationPort sendNotification = sent::add;

        new DailySummaryService(range -> D1_PERFORMANCE, getPortfolio, sendNotification, CLOCK, PARIS).send();

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
        List<DailySummary> sent = new ArrayList<>();

        new DailySummaryService(range -> D1_PERFORMANCE, getPortfolio, sent::add, CLOCK, PARIS).send();

        assertThat(sent.getFirst().cashOnlyAccounts()).isEmpty();
    }

    @Test
    void aLineWithNoQuoteYetIsIgnored() {
        GetPortfolioUseCase getPortfolio = () -> portfolioOf(
                List.of(line(LIVRET_A, EUROS, "20000", Optional.of(par())), line(LDDS, ETF, "10", Optional.empty())));
        List<DailySummary> sent = new ArrayList<>();

        new DailySummaryService(range -> D1_PERFORMANCE, getPortfolio, sent::add, CLOCK, PARIS).send();

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
        List<DailySummary> sent = new ArrayList<>();

        new DailySummaryService(range -> D1_PERFORMANCE, getPortfolio, sent::add, CLOCK, PARIS).send();

        assertThat(sent.getFirst().cashOnlyAccounts())
                .containsExactly(
                        new CashOnlyAccount("Livret A", AccountType.SAVINGS, Money.eur(new BigDecimal("20000"))));
    }

    private static ValuedHolding line(Account account, Instrument instrument, String quantity, Optional<Quote> quote) {
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrument.id(), new BigDecimal(quantity), null);
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
