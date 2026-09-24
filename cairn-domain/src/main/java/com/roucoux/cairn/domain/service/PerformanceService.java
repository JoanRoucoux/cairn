package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PerformanceService implements GetPerformanceUseCase {

    private final GetPortfolioUseCase getPortfolio;
    private final LoadQuotesPort loadQuotes;
    private final Clock clock;
    private final ZoneId zone;

    public PerformanceService(GetPortfolioUseCase getPortfolio, LoadQuotesPort loadQuotes, Clock clock, ZoneId zone) {
        this.getPortfolio = getPortfolio;
        this.loadQuotes = loadQuotes;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public Performance performance(PerformanceRange range) {
        Portfolio portfolio = getPortfolio.get();
        List<ValuedHolding> valuedLines = portfolio.holdings().stream()
                .filter(line -> line.marketValue().isPresent())
                .toList();

        LocalDate to = range.to(clock, zone);
        LocalDate from;
        List<LineMove> moves;
        if (range == PerformanceRange.D1) {
            from = to.minusDays(1);
            moves = dayMoves(valuedLines);
        } else {
            RangeStart start = rangeStart(range, valuedLines, to);
            from = start.from();
            moves = rangeMoves(valuedLines, start);
        }

        Money totalChange = sumChange(moves);

        Optional<Instant> lastPriceAt = valuedLines.stream()
                .filter(line -> !line.instrument().isPricedAtPar())
                .flatMap(line -> line.quote().stream())
                .map(Quote::fetchedAt)
                .max(Comparator.naturalOrder());

        return new Performance(
                range,
                from,
                to,
                range.isReconstructed(),
                lastPriceAt,
                portfolio.total(),
                totalChange,
                ratio(totalChange, sumStart(moves)),
                byEnvelope(moves, portfolio.total()));
    }

    private static List<LineMove> dayMoves(List<ValuedHolding> valuedLines) {
        return valuedLines.stream()
                .map(line -> new LineMove(line, dayStart(line)))
                .toList();
    }

    private static Optional<Money> dayStart(ValuedHolding line) {
        return line.dayChange().map(change -> line.marketValue().orElseThrow().minus(change));
    }

    /**
     * The effective start date, aligned with the date {@link HistoryService}'s constant-mix curve
     * starts at: the later of the range's own start and the latest first-quote date among the
     * valued, non-par lines (a younger line can't be priced any earlier than it was first quoted).
     * For {@link PerformanceRange#MAX} there is no range start, so this latest first-quote date is
     * the start outright. When no valued line carries a real quote at all (an all-cash or empty
     * portfolio), the start is simply today.
     */
    private RangeStart rangeStart(PerformanceRange range, List<ValuedHolding> valuedLines, LocalDate to) {
        Set<UUID> quotedInstruments = valuedLines.stream()
                .filter(line -> !line.instrument().isPricedAtPar())
                .map(line -> line.instrument().id())
                .collect(Collectors.toSet());
        Map<UUID, LocalDate> firstQuoteDates = loadQuotes.findFirstQuoteDates(quotedInstruments);
        Optional<LocalDate> latestFirstQuote = firstQuoteDates.values().stream().max(Comparator.naturalOrder());
        if (latestFirstQuote.isEmpty()) {
            return new RangeStart(to, Map.of(), false);
        }

        LocalDate rangeFrom = range.from(clock, zone).orElse(null);
        LocalDate from =
                rangeFrom == null || rangeFrom.isBefore(latestFirstQuote.get()) ? latestFirstQuote.get() : rangeFrom;
        Map<UUID, Quote> baseQuotes = loadQuotes.findLatestOnOrBefore(quotedInstruments, from);
        return new RangeStart(from, baseQuotes, true);
    }

    private static List<LineMove> rangeMoves(List<ValuedHolding> valuedLines, RangeStart start) {
        return valuedLines.stream()
                .map(line -> new LineMove(line, rangeStartValue(line, start)))
                .toList();
    }

    private static Optional<Money> rangeStartValue(ValuedHolding line, RangeStart start) {
        if (!start.anyQuoted()) {
            return Optional.empty();
        }
        if (line.instrument().isPricedAtPar()) {
            return Optional.of(line.marketValue().orElseThrow());
        }
        Quote base = start.baseQuotes().get(line.instrument().id());
        return Optional.ofNullable(base)
                .map(quote -> new Money(line.holding().quantity().multiply(quote.price()), quote.currency()));
    }

    private static Money sumChange(List<LineMove> moves) {
        return moves.stream().flatMap(move -> move.change().stream()).reduce(Money.zeroEur(), Money::plus);
    }

    private static Money sumStart(List<LineMove> moves) {
        return moves.stream().flatMap(move -> move.start().stream()).reduce(Money.zeroEur(), Money::plus);
    }

    private static List<EnvelopePerformance> byEnvelope(List<LineMove> moves, Money total) {
        Map<AccountType, List<LineMove>> grouped = moves.stream()
                .collect(Collectors.groupingBy(
                        move -> move.line().account().type(), LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    AccountType type = entry.getKey();
                    List<LineMove> group = entry.getValue();
                    Money value = group.stream().map(LineMove::current).reduce(Money.zeroEur(), Money::plus);
                    Money change = sumChange(group);
                    Money start = sumStart(group);
                    return new EnvelopePerformance(
                            type, value, Shares.share(value, total), change, ratio(change, start));
                })
                .sorted(Comparator.comparing((EnvelopePerformance envelope) ->
                                envelope.value().amount())
                        .reversed())
                .toList();
    }

    private static Optional<BigDecimal> ratio(Money change, Money start) {
        return start.amount().signum() == 0
                ? Optional.empty()
                : Optional.of(change.amount().divide(start.amount(), Shares.SCALE, RoundingMode.HALF_UP));
    }

    /** A line's current value against its value at the start of the range; absent without a start. */
    private record LineMove(ValuedHolding line, Optional<Money> start) {
        Money current() {
            return line.marketValue().orElseThrow();
        }

        Optional<Money> change() {
            return start.map(current()::minus);
        }
    }

    private record RangeStart(LocalDate from, Map<UUID, Quote> baseQuotes, boolean anyQuoted) {}
}
