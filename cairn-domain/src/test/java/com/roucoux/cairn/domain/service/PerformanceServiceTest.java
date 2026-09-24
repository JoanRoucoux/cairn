package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerformanceServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK =
            Clock.fixed(LocalDate.of(2026, 9, 24).atTime(20, 0).atZone(ZONE).toInstant(), ZONE);
    private static final Account ACCOUNT_1 = new Account(UUID.randomUUID(), "Broker One", AccountType.CTO, "Broker");
    private static final Account ACCOUNT_2 = new Account(UUID.randomUUID(), "Broker Two", AccountType.PEA, "Broker");
    private static final UUID PLACEHOLDER_INSTRUMENT_ID = UUID.randomUUID();

    @Test
    void oneDayChangeSumsEveryLinesDayChangeAndEnvelopesSumToTheTotal() {
        Line equityWithPreviousQuote =
                equityLine(ACCOUNT_1, "55.00", "10", List.of(quoteOn(LocalDate.of(2026, 9, 23), "50.00")));
        Line equityWithoutPreviousQuote = equityLine(ACCOUNT_2, "20.00", "5", List.of());

        Fixture fixture = new Fixture(List.of(equityWithPreviousQuote, equityWithoutPreviousQuote));
        Performance performance = fixture.service().performance(PerformanceRange.D1);

        assertThat(performance.total().amount()).isEqualByComparingTo("650");
        assertThat(performance.change().amount()).isEqualByComparingTo("50");
        assertThat(performance.byEnvelope().stream()
                        .map(EnvelopePerformance::value)
                        .map(Money::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(performance.total().amount());
        assertThat(performance.byEnvelope().stream()
                        .map(EnvelopePerformance::change)
                        .map(Money::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(performance.change().amount());
    }

    @Test
    void oneMonthChangeIsCurrentValueMinusValueAtTheEffectiveStart() {
        LocalDate from = LocalDate.now(CLOCK).minusDays(31);
        Line pricedAtFrom = equityLine(
                ACCOUNT_1, "55.00", "10", List.of(quoteOn(from.minusYears(1), "40.00"), quoteOn(from, "50.00")));

        Fixture fixture = new Fixture(List.of(pricedAtFrom));
        Performance performance = fixture.service().performance(PerformanceRange.M1);

        assertThat(performance.from()).isEqualTo(from);
        assertThat(performance.total().amount()).isEqualByComparingTo("550");
        assertThat(performance.change().amount()).isEqualByComparingTo("50");
    }

    @Test
    void oneMonthEffectiveStartIsPushedBackToAYoungLinesFirstQuote() {
        LocalDate firstQuote = LocalDate.now(CLOCK).minusDays(10);
        Line young = equityLine(ACCOUNT_1, "55.00", "10", List.of(quoteOn(firstQuote, "50.00")));

        Fixture fixture = new Fixture(List.of(young));
        Performance performance = fixture.service().performance(PerformanceRange.M1);

        assertThat(performance.from()).isEqualTo(firstQuote);
        assertThat(performance.change().amount()).isEqualByComparingTo("50");
    }

    @Test
    void manualCashAtParCountsAtOneAtBothEndsForAZeroChange() {
        Line cash = cashLine(ACCOUNT_1, "1000");

        Fixture fixture = new Fixture(List.of(cash));
        Performance performance = fixture.service().performance(PerformanceRange.M1);

        assertThat(performance.total().amount()).isEqualByComparingTo("1000");
        assertThat(performance.change().amount()).isEqualByComparingTo("0");
    }

    @Test
    void fromIsTodayAndTheChangeIsZeroWithAnAbsentRatioWhenNothingIsQuoted() {
        Line cash = cashLine(ACCOUNT_1, "1000");

        Fixture fixture = new Fixture(List.of(cash));
        Performance performance = fixture.service().performance(PerformanceRange.MAX);

        assertThat(performance.from()).isEqualTo(LocalDate.now(CLOCK));
        assertThat(performance.change().amount()).isEqualByComparingTo("0");
        assertThat(performance.changeRatio()).isEmpty();
    }

    @Test
    void leavesTheChangeRatioEmptyWhenTheStartValueIsZero() {
        LocalDate from = LocalDate.now(CLOCK).minusDays(31);
        Line line = equityLine(ACCOUNT_1, "55.00", "10", List.of(quoteOn(from, "0")));

        Fixture fixture = new Fixture(List.of(line));
        Performance performance = fixture.service().performance(PerformanceRange.M1);

        assertThat(performance.changeRatio()).isEmpty();
    }

    @Test
    void aFlatYoungLineStillCountsInTheRatiosBaseForAOneMonthRange() {
        LocalDate wellBeforeRange = LocalDate.now(CLOCK).minusDays(40);
        LocalDate tenDaysAgo = LocalDate.now(CLOCK).minusDays(10);
        Line matured = equityLine(ACCOUNT_1, "110.00", "1", List.of(quoteOn(wellBeforeRange, "100.00")));
        Line flatSinceItsOnlyQuote = equityLine(ACCOUNT_1, "50.00", "1", List.of(quoteOn(tenDaysAgo, "50.00")));

        Fixture fixture = new Fixture(List.of(matured, flatSinceItsOnlyQuote));
        Performance performance = fixture.service().performance(PerformanceRange.M1);

        assertThat(performance.change().amount()).isEqualByComparingTo("10");
        assertThat(performance.changeRatio())
                .hasValueSatisfying(ratio -> assertThat(ratio).isEqualByComparingTo("0.0666666667"));
        assertThat(performance.byEnvelope()).hasSize(1);
        assertThat(performance.byEnvelope().getFirst().change().amount()).isEqualByComparingTo("10");
        assertThat(performance.byEnvelope().getFirst().changeRatio())
                .hasValueSatisfying(ratio -> assertThat(ratio).isEqualByComparingTo("0.0666666667"));
    }

    @Test
    void marksFiveYearsAndMaxAsReconstructedButNotShorterRanges() {
        Fixture fixture = new Fixture(List.of(equityLine(ACCOUNT_1, "55.00", "10", List.of())));

        assertThat(fixture.service().performance(PerformanceRange.D1).reconstructed())
                .isFalse();
        assertThat(fixture.service().performance(PerformanceRange.D7).reconstructed())
                .isFalse();
        assertThat(fixture.service().performance(PerformanceRange.M1).reconstructed())
                .isFalse();
        assertThat(fixture.service().performance(PerformanceRange.Y1).reconstructed())
                .isFalse();
        assertThat(fixture.service().performance(PerformanceRange.Y5).reconstructed())
                .isTrue();
        assertThat(fixture.service().performance(PerformanceRange.MAX).reconstructed())
                .isTrue();
    }

    /**
     * The reviewer's example: A is quoted well before B, but max's start is pinned to the later of
     * the two first-quote dates (B's), exactly where {@code HistoryService}'s constant-mix curve
     * would start too.
     */
    @Test
    void maxStartsAtTheLatestFirstQuoteDateAmongTheLinesLikeTheHistoryCurveDoes() {
        Line a = equityLine(
                ACCOUNT_1,
                "55.00",
                "10",
                List.of(quoteOn(LocalDate.of(2020, 1, 15), "40.00"), quoteOn(LocalDate.of(2024, 1, 2), "48.00")));
        Line b = equityLine(ACCOUNT_2, "110.00", "1", List.of(quoteOn(LocalDate.of(2024, 1, 2), "100.00")));

        Fixture fixture = new Fixture(List.of(a, b));
        Performance performance = fixture.service().performance(PerformanceRange.MAX);

        assertThat(performance.from()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(performance.change().amount()).isEqualByComparingTo("80");
    }

    @Test
    void lastPriceAtIgnoresManualCashAtPar() {
        Instant fetchedAt = CLOCK.instant().minusSeconds(60);
        Line equity = equityLine(ACCOUNT_1, "55.00", "10", List.of(), fetchedAt);
        Line cash = cashLine(ACCOUNT_2, "1000");

        Fixture fixture = new Fixture(List.of(equity, cash));
        Performance performance = fixture.service().performance(PerformanceRange.D1);

        assertThat(performance.lastPriceAt()).contains(fetchedAt);
    }

    @Test
    void anchorsTodayInTheConfiguredZoneEvenWhenUtcIsStillOnThePreviousDay() {
        // 00:30 in Europe/Paris (CEST, +2) is 22:30 UTC the day before.
        ZoneId paris = ZoneId.of("Europe/Paris");
        Instant justAfterMidnightInParis =
                LocalDate.of(2026, 6, 16).atTime(0, 30).atZone(paris).toInstant();
        Clock clock = Clock.fixed(justAfterMidnightInParis, ZoneId.of("UTC"));
        Fixture fixture = new Fixture(List.of(equityLine(ACCOUNT_1, "55.00", "10", List.of())), clock, paris);

        Performance performance = fixture.service().performance(PerformanceRange.D1);

        assertThat(performance.to()).isEqualTo(LocalDate.of(2026, 6, 16));
    }

    @Test
    void sortsEnvelopesByValueDescending() {
        Line small = equityLine(ACCOUNT_2, "20.00", "5", List.of());
        Line large = equityLine(ACCOUNT_1, "55.00", "10", List.of());

        Fixture fixture = new Fixture(List.of(small, large));
        Performance performance = fixture.service().performance(PerformanceRange.D1);

        assertThat(performance.byEnvelope())
                .extracting(EnvelopePerformance::accountType)
                .containsExactly(AccountType.CTO, AccountType.PEA);
    }

    private static Quote quoteOn(LocalDate asOf, String price) {
        return new Quote(
                PLACEHOLDER_INSTRUMENT_ID, asOf, new BigDecimal(price), "EUR", PriceSource.YAHOO, CLOCK.instant());
    }

    private record Line(Holding holding, Instrument instrument, Account account, Quote current, List<Quote> history) {}

    private static Line equityLine(Account account, String price, String quantity, List<Quote> history) {
        return equityLine(account, price, quantity, history, CLOCK.instant());
    }

    private static Line equityLine(
            Account account, String price, String quantity, List<Quote> history, Instant fetchedAt) {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument(
                instrumentId, "Test", null, "EUR", AssetClass.EQUITY, PriceSource.YAHOO, "TEST.PA", null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, new BigDecimal(quantity), null);
        Quote current = new Quote(
                instrumentId, LocalDate.now(CLOCK), new BigDecimal(price), "EUR", PriceSource.YAHOO, fetchedAt);
        List<Quote> withId = history.stream()
                .map(q -> new Quote(instrumentId, q.asOf(), q.price(), q.currency(), q.source(), q.fetchedAt()))
                .toList();
        return new Line(holding, instrument, account, current, withId);
    }

    private static Line cashLine(Account account, String quantity) {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument =
                new Instrument(instrumentId, "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, new BigDecimal(quantity), null);
        return new Line(holding, instrument, account, null, List.of());
    }

    private static final class Fixture {
        private final List<Line> lines;
        private final Clock clock;
        private final ZoneId zone;

        private Fixture(List<Line> lines) {
            this(lines, CLOCK, ZONE);
        }

        private Fixture(List<Line> lines, Clock clock, ZoneId zone) {
            this.lines = lines;
            this.clock = clock;
            this.zone = zone;
        }

        private PerformanceService service() {
            List<Holding> holdings = lines.stream().map(Line::holding).toList();
            List<Instrument> instruments = lines.stream().map(Line::instrument).toList();
            List<Account> accounts =
                    lines.stream().map(Line::account).distinct().toList();

            Map<UUID, List<Quote>> history = new LinkedHashMap<>();
            for (Line line : lines) {
                if (line.current() == null) {
                    continue;
                }
                List<Quote> series = new ArrayList<>(line.history());
                series.add(line.current());
                series.sort(Comparator.comparing(Quote::asOf));
                history.put(line.instrument().id(), series);
            }

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
                    return Optional.empty();
                }

                @Override
                public List<Holding> findByInstrument(UUID instrumentId) {
                    return List.of();
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
                    return history.getOrDefault(instrumentId, List.of()).stream()
                            .max(Comparator.comparing(Quote::asOf));
                }

                @Override
                public Optional<Quote> findPrevious(UUID instrumentId, LocalDate before) {
                    return history.getOrDefault(instrumentId, List.of()).stream()
                            .filter(q -> q.asOf().isBefore(before))
                            .max(Comparator.comparing(Quote::asOf));
                }

                @Override
                public List<Quote> findBetween(UUID instrumentId, LocalDate from, LocalDate to) {
                    return history.getOrDefault(instrumentId, List.of()).stream()
                            .filter(q -> !q.asOf().isBefore(from) && !q.asOf().isAfter(to))
                            .toList();
                }

                @Override
                public Map<UUID, List<Quote>> findBetweenForAll(Set<UUID> instrumentIds, LocalDate from, LocalDate to) {
                    Map<UUID, List<Quote>> result = new LinkedHashMap<>();
                    for (UUID id : instrumentIds) {
                        result.put(id, findBetween(id, from, to));
                    }
                    return result;
                }

                @Override
                public Map<UUID, Quote> findLatestOnOrBefore(Set<UUID> instrumentIds, LocalDate day) {
                    Map<UUID, Quote> result = new LinkedHashMap<>();
                    for (UUID id : instrumentIds) {
                        history.getOrDefault(id, List.of()).stream()
                                .filter(q -> !q.asOf().isAfter(day))
                                .max(Comparator.comparing(Quote::asOf))
                                .ifPresent(q -> result.put(id, q));
                    }
                    return result;
                }

                @Override
                public Map<UUID, LocalDate> findFirstQuoteDates(Set<UUID> instrumentIds) {
                    Map<UUID, LocalDate> result = new LinkedHashMap<>();
                    for (UUID id : instrumentIds) {
                        history.getOrDefault(id, List.of()).stream()
                                .min(Comparator.comparing(Quote::asOf))
                                .ifPresent(q -> result.put(id, q.asOf()));
                    }
                    return result;
                }
            };

            HoldingValuationService valueHolding =
                    new HoldingValuationService(loadInstruments, loadAccounts, loadQuotes, clock);
            GetPortfolioUseCase getPortfolio = new PortfolioService(loadHoldings, valueHolding, clock);
            return new PerformanceService(getPortfolio, loadQuotes, clock, zone);
        }
    }
}
