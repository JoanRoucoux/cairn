package com.roucoux.cairn.domain.service;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toSet;

import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.HistoryPoint;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.GetHistoryUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.LoadSnapshotsPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HistoryService implements GetHistoryUseCase {

    private final LoadHoldingsPort loadHoldings;
    private final LoadQuotesPort loadQuotes;
    private final LoadSnapshotsPort loadSnapshots;

    public HistoryService(LoadHoldingsPort loadHoldings, LoadQuotesPort loadQuotes, LoadSnapshotsPort loadSnapshots) {
        this.loadHoldings = loadHoldings;
        this.loadQuotes = loadQuotes;
        this.loadSnapshots = loadSnapshots;
    }

    @Override
    public List<HistoryPoint> history(HistoryMode mode, LocalDate from, LocalDate to) {
        return mode == HistoryMode.SNAPSHOT ? fromSnapshots(from, to) : constantMix(from, to);
    }

    private List<HistoryPoint> fromSnapshots(LocalDate from, LocalDate to) {
        return loadSnapshots.findBetween(from, to).stream()
                .map(snapshot -> new HistoryPoint(snapshot.date(), snapshot.totalEur()))
                .toList();
    }

    private List<HistoryPoint> constantMix(LocalDate from, LocalDate to) {
        List<Holding> holdings = loadHoldings.findAll();
        Map<UUID, List<Quote>> quotes = loadQuotes.findBetweenForAll(
                holdings.stream().map(Holding::instrumentId).collect(toSet()), from, to);

        List<Holding> priceable = holdings.stream()
                .filter(holding ->
                        !quotes.getOrDefault(holding.instrumentId(), List.of()).isEmpty())
                .toList();
        if (priceable.isEmpty()) {
            return List.of();
        }

        LocalDate start = priceable.stream()
                .map(holding -> quotes.get(holding.instrumentId()).getFirst().asOf())
                .max(naturalOrder())
                .orElseThrow();

        Map<UUID, Iterator<Quote>> cursors = new HashMap<>();
        Map<UUID, Quote> pending = new HashMap<>();
        Map<UUID, BigDecimal> lastPrice = new HashMap<>();
        priceable.forEach(holding -> {
            Iterator<Quote> cursor = quotes.get(holding.instrumentId()).iterator();
            cursors.put(holding.instrumentId(), cursor);
            pending.put(holding.instrumentId(), cursor.next());
        });

        List<HistoryPoint> series = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(to); day = day.plusDays(1)) {
            for (Holding holding : priceable) {
                advanceTo(holding.instrumentId(), day, cursors, pending, lastPrice);
            }
            BigDecimal total = priceable.stream()
                    .map(holding -> holding.quantity().multiply(lastPrice.get(holding.instrumentId())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            series.add(new HistoryPoint(day, total));
        }
        return series;
    }

    /**
     * Advances the instrument's cursor to the last quote whose date is on or before {@code day},
     * remembering its price: the carry-forward of the last known quote.
     */
    private static void advanceTo(
            UUID instrumentId,
            LocalDate day,
            Map<UUID, Iterator<Quote>> cursors,
            Map<UUID, Quote> pending,
            Map<UUID, BigDecimal> lastPrice) {
        Quote candidate = pending.get(instrumentId);
        Iterator<Quote> cursor = cursors.get(instrumentId);
        while (candidate != null && !candidate.asOf().isAfter(day)) {
            lastPrice.put(instrumentId, candidate.price());
            candidate = cursor.hasNext() ? cursor.next() : null;
        }
        if (candidate == null) {
            pending.remove(instrumentId);
        } else {
            pending.put(instrumentId, candidate);
        }
    }
}
