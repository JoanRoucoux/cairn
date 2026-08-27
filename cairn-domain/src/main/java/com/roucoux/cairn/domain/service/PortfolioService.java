package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Allocation;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PortfolioService implements GetPortfolioUseCase {

    private static final int SHARE_SCALE = 10;

    private final LoadHoldingsPort loadHoldings;
    private final ValueHoldingUseCase valueHolding;
    private final Clock clock;

    public PortfolioService(LoadHoldingsPort loadHoldings, ValueHoldingUseCase valueHolding, Clock clock) {
        this.loadHoldings = loadHoldings;
        this.valueHolding = valueHolding;
        this.clock = clock;
    }

    @Override
    public Portfolio get() {
        List<ValuedHolding> lines = loadHoldings.findAll().stream()
                .flatMap(holding -> valueHolding.value(holding).stream())
                .toList();

        Money total = lines.stream().map(ValuedHolding::marketValue).reduce(Money.zeroEur(), Money::plus);

        return new Portfolio(
                total,
                lines.stream().flatMap(line -> line.dayChange().stream()).reduce(Money.zeroEur(), Money::plus),
                unrealizedGain(lines),
                allocate(lines, total, line -> line.instrument().assetClass().name()),
                allocate(lines, total, line -> line.account().name()),
                lines,
                (int) lines.stream().filter(line -> line.isStale(clock)).count());
    }

    private static Optional<Money> unrealizedGain(List<ValuedHolding> lines) {
        if (lines.stream().anyMatch(line -> line.unrealizedGain().isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(
                lines.stream().map(line -> line.unrealizedGain().orElseThrow()).reduce(Money.zeroEur(), Money::plus));
    }

    private static List<Allocation> allocate(
            List<ValuedHolding> lines, Money total, Function<ValuedHolding, String> by) {
        Map<String, Money> grouped = new LinkedHashMap<>();
        for (ValuedHolding line : lines) {
            grouped.merge(by.apply(line), line.marketValue(), Money::plus);
        }
        return grouped.entrySet().stream()
                .map(entry -> new Allocation(entry.getKey(), entry.getValue(), share(entry.getValue(), total)))
                .sorted(Comparator.comparing(
                                (Allocation allocation) -> allocation.value().amount())
                        .reversed())
                .toList();
    }

    private static BigDecimal share(Money part, Money total) {
        return total.amount().signum() == 0
                ? BigDecimal.ZERO
                : part.amount().divide(total.amount(), SHARE_SCALE, RoundingMode.HALF_UP);
    }
}
