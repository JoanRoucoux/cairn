package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Snapshot;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.ComputeSnapshotUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.out.SaveSnapshotPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SnapshotService implements ComputeSnapshotUseCase {

    private final GetPortfolioUseCase getPortfolio;
    private final SaveSnapshotPort saveSnapshot;
    private final Clock clock;

    public SnapshotService(GetPortfolioUseCase getPortfolio, SaveSnapshotPort saveSnapshot, Clock clock) {
        this.getPortfolio = getPortfolio;
        this.saveSnapshot = saveSnapshot;
        this.clock = clock;
    }

    @Override
    public Snapshot compute() {
        List<ValuedHolding> valuedLines = getPortfolio.get().holdings().stream()
                .filter(line -> line.marketValue().isPresent())
                .toList();

        Snapshot snapshot = new Snapshot(
                LocalDate.now(clock),
                sum(valuedLines),
                breakdown(valuedLines, line -> line.account().type().name()),
                breakdown(valuedLines, line -> line.instrument().assetClass().name()));
        saveSnapshot.save(snapshot);
        return snapshot;
    }

    private static BigDecimal sum(List<ValuedHolding> lines) {
        return lines.stream()
                .map(line -> line.marketValue().orElseThrow().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, BigDecimal> breakdown(List<ValuedHolding> lines, Function<ValuedHolding, String> by) {
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();
        for (ValuedHolding line : lines) {
            grouped.merge(by.apply(line), line.marketValue().orElseThrow().amount(), BigDecimal::add);
        }
        return grouped;
    }
}
