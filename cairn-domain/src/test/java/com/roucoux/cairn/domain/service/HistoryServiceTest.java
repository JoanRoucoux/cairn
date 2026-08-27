package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.HistoryPoint;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.Snapshot;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.LoadSnapshotsPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryServiceTest {

    private static final UUID CW8_ID = UUID.randomUUID();
    private static final UUID FCPE_ID = UUID.randomUUID();
    private static final UUID CASH_ID = UUID.randomUUID();
    private static final LocalDate FAR_FUTURE = LocalDate.of(2026, 8, 21);

    @Test
    void valuesEachDayAtTheQuantitiesHeldToday() {
        // 29 CW8 : le 20 a 690.81, le 21 a 686.31
        HistoryService service = serviceWith(
                holding(CW8_ID, new BigDecimal("29")),
                quotes(
                        CW8_ID,
                        Map.of(
                                LocalDate.of(2026, 8, 20), new BigDecimal("690.81"),
                                LocalDate.of(2026, 8, 21), new BigDecimal("686.31"))));

        List<HistoryPoint> series =
                service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));

        assertThat(series)
                .extracting(HistoryPoint::totalEur)
                .containsExactly(new BigDecimal("20033.49"), new BigDecimal("19902.99"));
    }

    @Test
    void carriesTheLastKnownPriceForwardOnDaysWithoutAQuote() {
        // Un FCPE ne publie pas le week-end : la VL du vendredi vaut pour samedi et dimanche.
        HistoryService service = serviceWith(
                holding(FCPE_ID, new BigDecimal("100")),
                quotes(FCPE_ID, Map.of(LocalDate.of(2026, 8, 21), new BigDecimal("68.34"))));

        List<HistoryPoint> series =
                service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 23));

        assertThat(series).hasSize(3);
        assertThat(series).extracting(HistoryPoint::totalEur).containsOnly(new BigDecimal("6834.00"));
    }

    @Test
    void startsOnlyOnceEveryHoldingCanBePriced() {
        // Without this rule, the curve would show a false ramp: the total would climb simply
        // because the instruments appear one after another.
        HistoryService service = serviceWith(
                List.of(holding(CW8_ID, BigDecimal.ONE), holding(FCPE_ID, BigDecimal.ONE)),
                Map.of(
                        CW8_ID, quotesFrom(LocalDate.of(2020, 1, 1)),
                        FCPE_ID, quotesFrom(LocalDate.of(2024, 6, 1))));

        List<HistoryPoint> series =
                service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2019, 1, 1), LocalDate.of(2026, 8, 21));

        assertThat(series.getFirst().date()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void ignoresAHoldingWhoseInstrumentHasNoQuoteAtAll() {
        HistoryService service = serviceWith(
                List.of(holding(CW8_ID, BigDecimal.ONE), holding(CASH_ID, new BigDecimal("732.40"))),
                Map.of(CW8_ID, quotesFrom(LocalDate.of(2026, 8, 20))));

        assertThat(service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21)))
                .isNotEmpty();
    }

    @Test
    void returnsAnEmptySeriesWhenNothingCanBePriced() {
        HistoryService service = serviceWith(List.of(), Map.of());

        assertThat(service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 21)))
                .isEmpty();
    }

    @Test
    void readsTheSnapshotTableInSnapshotMode() {
        HistoryService service = serviceWithSnapshots(
                List.of(new Snapshot(LocalDate.of(2026, 8, 21), new BigDecimal("278146.45"), Map.of(), Map.of())));

        assertThat(service.history(HistoryMode.SNAPSHOT, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 21)))
                .singleElement()
                .satisfies(point -> assertThat(point.totalEur()).isEqualByComparingTo("278146.45"));
    }

    private static Holding holding(UUID instrumentId, BigDecimal quantity) {
        return new Holding(UUID.randomUUID(), UUID.randomUUID(), instrumentId, quantity, null);
    }

    private static Map<UUID, List<Quote>> quotes(UUID instrumentId, Map<LocalDate, BigDecimal> pricesByDate) {
        List<Quote> series = pricesByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> quote(instrumentId, entry.getKey(), entry.getValue()))
                .toList();
        return Map.of(instrumentId, series);
    }

    private static List<Quote> quotesFrom(LocalDate from) {
        List<Quote> series = new java.util.ArrayList<>();
        for (LocalDate day = from; !day.isAfter(FAR_FUTURE); day = day.plusDays(1)) {
            series.add(quote(UUID.randomUUID(), day, BigDecimal.TEN));
        }
        return series;
    }

    private static Quote quote(UUID instrumentId, LocalDate asOf, BigDecimal price) {
        return new Quote(instrumentId, asOf, price, "EUR", PriceSource.YAHOO, Instant.parse("2026-08-21T00:00:00Z"));
    }

    private static HistoryService serviceWith(Holding holding, Map<UUID, List<Quote>> quotesByInstrument) {
        return serviceWith(List.of(holding), quotesByInstrument);
    }

    private static HistoryService serviceWith(List<Holding> holdings, Map<UUID, List<Quote>> quotesByInstrument) {
        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
            @Override
            public List<Holding> findAll() {
                return holdings;
            }

            @Override
            public Optional<Holding> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
                return Optional.empty();
            }
        };
        LoadQuotesPort loadQuotes = new LoadQuotesPort() {
            @Override
            public Optional<Quote> findLatest(UUID instrumentId) {
                return Optional.empty();
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
                Map<UUID, List<Quote>> result = new HashMap<>();
                for (UUID instrumentId : instrumentIds) {
                    List<Quote> inRange = quotesByInstrument.getOrDefault(instrumentId, List.of()).stream()
                            .filter(q -> !q.asOf().isBefore(from) && !q.asOf().isAfter(to))
                            .toList();
                    if (!inRange.isEmpty()) {
                        result.put(instrumentId, inRange);
                    }
                }
                return result;
            }
        };
        LoadSnapshotsPort loadSnapshots = (from, to) -> List.of();
        return new HistoryService(loadHoldings, loadQuotes, loadSnapshots);
    }

    private static HistoryService serviceWithSnapshots(List<Snapshot> snapshots) {
        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
            @Override
            public List<Holding> findAll() {
                return List.of();
            }

            @Override
            public Optional<Holding> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
                return Optional.empty();
            }
        };
        LoadQuotesPort loadQuotes = new LoadQuotesPort() {
            @Override
            public Optional<Quote> findLatest(UUID instrumentId) {
                return Optional.empty();
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
        LoadSnapshotsPort loadSnapshots = (from, to) -> snapshots.stream()
                .filter(snapshot ->
                        !snapshot.date().isBefore(from) && !snapshot.date().isAfter(to))
                .toList();
        return new HistoryService(loadHoldings, loadQuotes, loadSnapshots);
    }
}
