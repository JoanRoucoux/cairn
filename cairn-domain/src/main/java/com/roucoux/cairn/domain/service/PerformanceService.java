package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.NonEurHoldingException;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
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

    private static final int SHARE_SCALE = 10;
    /** Stands for "no lower bound" when reading quote history for {@link PerformanceRange#MAX}. */
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

    private final LoadHoldingsPort loadHoldings;
    private final ValueHoldingUseCase valueHolding;
    private final LoadQuotesPort loadQuotes;
    private final Clock clock;
    private final ZoneId zone;

    public PerformanceService(
            LoadHoldingsPort loadHoldings,
            ValueHoldingUseCase valueHolding,
            LoadQuotesPort loadQuotes,
            Clock clock,
            ZoneId zone) {
        this.loadHoldings = loadHoldings;
        this.valueHolding = valueHolding;
        this.loadQuotes = loadQuotes;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public Performance performance(PerformanceRange range) {
        List<ValuedHolding> lines = loadHoldings.findAll().stream()
                .flatMap(holding -> valueHolding.value(holding).stream())
                .toList();
        List<ValuedHolding> valuedLines =
                lines.stream().filter(line -> line.marketValue().isPresent()).toList();
        valuedLines.forEach(PerformanceService::requireEur);

        Money total = valuedLines.stream()
                .map(line -> line.marketValue().orElseThrow())
                .reduce(Money.zeroEur(), Money::plus);

        LocalDate to = range.to(clock, zone);
        Map<ValuedHolding, Optional<Money>> changes = changesByLine(range, valuedLines, to);

        LocalDate from = range.from(clock, zone).orElseGet(() -> earliestBaseline(valuedLines, to));

        Money totalChange = changes.values().stream().flatMap(Optional::stream).reduce(Money.zeroEur(), Money::plus);

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
                total,
                totalChange,
                ratio(totalChange, total),
                byEnvelope(valuedLines, changes, total));
    }

    private Map<ValuedHolding, Optional<Money>> changesByLine(
            PerformanceRange range, List<ValuedHolding> valuedLines, LocalDate to) {
        if (range == PerformanceRange.D1) {
            Map<ValuedHolding, Optional<Money>> changes = new LinkedHashMap<>();
            valuedLines.forEach(line -> changes.put(line, line.dayChange()));
            return changes;
        }

        LocalDate from = range.from(clock, zone).orElse(null);
        Set<UUID> quotedInstruments = valuedLines.stream()
                .filter(line -> !line.instrument().isPricedAtPar())
                .map(line -> line.instrument().id())
                .collect(Collectors.toSet());
        Map<UUID, Quote> baseQuotes = range == PerformanceRange.MAX
                ? earliestQuotes(quotedInstruments, to)
                : loadQuotes.findLatestOnOrBefore(quotedInstruments, from);

        Map<ValuedHolding, Optional<Money>> changes = new LinkedHashMap<>();
        for (ValuedHolding line : valuedLines) {
            changes.put(line, changeFor(line, baseQuotes));
        }
        return changes;
    }

    private Map<UUID, Quote> earliestQuotes(Set<UUID> instrumentIds, LocalDate to) {
        Map<UUID, List<Quote>> history = loadQuotes.findBetweenForAll(instrumentIds, EPOCH, to);
        Map<UUID, Quote> earliest = new LinkedHashMap<>();
        history.forEach((id, quotes) ->
                quotes.stream().min(Comparator.comparing(Quote::asOf)).ifPresent(quote -> earliest.put(id, quote)));
        return earliest;
    }

    private static Optional<Money> changeFor(ValuedHolding line, Map<UUID, Quote> baseQuotes) {
        if (line.instrument().isPricedAtPar()) {
            return Optional.of(
                    new Money(BigDecimal.ZERO, line.quote().orElseThrow().currency()));
        }
        Quote base = baseQuotes.get(line.instrument().id());
        if (base == null) {
            return Optional.empty();
        }
        Money current = line.marketValue().orElseThrow();
        Money baseValue = new Money(line.holding().quantity().multiply(base.price()), base.currency());
        return Optional.of(current.minus(baseValue));
    }

    /**
     * The earliest date any line's baseline is known, for {@link PerformanceRange#MAX}'s {@code
     * from}. Falls back to {@code to} when nothing is quoted yet (an all-cash or empty portfolio).
     */
    private LocalDate earliestBaseline(List<ValuedHolding> valuedLines, LocalDate to) {
        Set<UUID> quotedInstruments = valuedLines.stream()
                .filter(line -> !line.instrument().isPricedAtPar())
                .map(line -> line.instrument().id())
                .collect(Collectors.toSet());
        return earliestQuotes(quotedInstruments, to).values().stream()
                .map(Quote::asOf)
                .min(Comparator.naturalOrder())
                .orElse(to);
    }

    private static List<EnvelopePerformance> byEnvelope(
            List<ValuedHolding> valuedLines, Map<ValuedHolding, Optional<Money>> changes, Money total) {
        Map<AccountType, List<ValuedHolding>> grouped = valuedLines.stream()
                .collect(Collectors.groupingBy(line -> line.account().type(), LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    AccountType type = entry.getKey();
                    List<ValuedHolding> group = entry.getValue();
                    Money value = group.stream()
                            .map(line -> line.marketValue().orElseThrow())
                            .reduce(Money.zeroEur(), Money::plus);
                    Money change = group.stream()
                            .flatMap(line -> changes.get(line).stream())
                            .reduce(Money.zeroEur(), Money::plus);
                    return new EnvelopePerformance(type, value, share(value, total), change, ratio(change, value));
                })
                .sorted(Comparator.comparing((EnvelopePerformance envelope) ->
                                envelope.value().amount())
                        .reversed())
                .toList();
    }

    private static void requireEur(ValuedHolding line) {
        String currency = line.marketValue().orElseThrow().currency();
        if (!Money.EUR.equals(currency)) {
            throw new NonEurHoldingException(line.instrument().isin(), currency);
        }
    }

    private static BigDecimal share(Money part, Money total) {
        return total.amount().signum() == 0
                ? BigDecimal.ZERO
                : part.amount().divide(total.amount(), SHARE_SCALE, RoundingMode.HALF_UP);
    }

    private static Optional<BigDecimal> ratio(Money change, Money value) {
        BigDecimal base = value.amount().subtract(change.amount());
        return base.signum() == 0
                ? Optional.empty()
                : Optional.of(change.amount().divide(base, SHARE_SCALE, RoundingMode.HALF_UP));
    }
}
