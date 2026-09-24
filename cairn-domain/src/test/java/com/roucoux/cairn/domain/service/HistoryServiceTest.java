package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.HistoryPoint;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.Snapshot;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.LoadSnapshotsPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryServiceTest {

    private static final UUID ETF_ID = UUID.randomUUID();
    private static final UUID FCPE_ID = UUID.randomUUID();
    private static final UUID CASH_ID = UUID.randomUUID();
    private static final LocalDate FAR_FUTURE = LocalDate.of(2026, 8, 21);

    @Test
    void valuesEachDayAtTheQuantitiesHeldToday() {
        HistoryService service = serviceWith(
                holding(ETF_ID, new BigDecimal("29")),
                quotes(
                        ETF_ID,
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
        HistoryService service = serviceWith(
                List.of(holding(ETF_ID, BigDecimal.ONE), holding(FCPE_ID, BigDecimal.ONE)),
                Map.of(
                        ETF_ID, quotesFrom(LocalDate.of(2020, 1, 1)),
                        FCPE_ID, quotesFrom(LocalDate.of(2024, 6, 1))));

        List<HistoryPoint> series =
                service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2019, 1, 1), LocalDate.of(2026, 8, 21));

        assertThat(series.getFirst().date()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void ignoresAHoldingWhoseInstrumentHasNoQuoteAtAll() {
        HistoryService service = serviceWith(
                List.of(holding(ETF_ID, BigDecimal.ONE), holding(CASH_ID, new BigDecimal("732.40"))),
                Map.of(ETF_ID, quotesFrom(LocalDate.of(2026, 8, 20))),
                List.of(
                        instrument(ETF_ID, AssetClass.EQUITY, PriceSource.YAHOO),
                        instrument(CASH_ID, AssetClass.FUND, PriceSource.MANUAL)));

        assertThat(service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21)))
                .extracting(HistoryPoint::totalEur)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10"), new BigDecimal("10"));
    }

    @Test
    void seedsALineWithItsLastQuoteBeforeTheWindow() {
        HistoryService service = serviceWith(
                holding(FCPE_ID, new BigDecimal("100")),
                quotes(FCPE_ID, Map.of(LocalDate.of(2026, 9, 18), new BigDecimal("60.39"))));

        List<HistoryPoint> series =
                service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));

        assertThat(series)
                .extracting(HistoryPoint::date)
                .containsExactly(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));
        assertThat(series).extracting(HistoryPoint::totalEur).containsOnly(new BigDecimal("6039.00"));
    }

    @Test
    void countsManualCashAtParOnEveryDay() {
        HistoryService service = serviceWith(
                List.of(holding(ETF_ID, BigDecimal.ONE), holding(CASH_ID, new BigDecimal("20000"))),
                Map.of(
                        ETF_ID,
                        List.of(
                                quote(ETF_ID, LocalDate.of(2026, 9, 22), new BigDecimal("703.73")),
                                quote(ETF_ID, LocalDate.of(2026, 9, 23), new BigDecimal("705.00")))),
                List.of(
                        instrument(ETF_ID, AssetClass.ETF, PriceSource.YAHOO),
                        instrument(CASH_ID, AssetClass.CASH, PriceSource.MANUAL)));

        List<HistoryPoint> series =
                service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));

        assertThat(series)
                .extracting(HistoryPoint::totalEur)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("20703.73"), new BigDecimal("20705.00"));
    }

    @Test
    void neverStartsBeforeTheRequestedDay() {
        HistoryService service = serviceWith(
                holding(ETF_ID, BigDecimal.ONE), quotes(ETF_ID, Map.of(LocalDate.of(2026, 9, 1), BigDecimal.TEN)));

        List<HistoryPoint> series =
                service.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));

        assertThat(series.getFirst().date()).isEqualTo(LocalDate.of(2026, 9, 22));
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
        List<Quote> series = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(FAR_FUTURE); day = day.plusDays(1)) {
            series.add(quote(UUID.randomUUID(), day, BigDecimal.TEN));
        }
        return series;
    }

    private static Quote quote(UUID instrumentId, LocalDate asOf, BigDecimal price) {
        return new Quote(instrumentId, asOf, price, "EUR", PriceSource.YAHOO, Instant.parse("2026-08-21T00:00:00Z"));
    }

    private static Instrument instrument(UUID id, AssetClass assetClass, PriceSource source) {
        String ref = source == PriceSource.MANUAL ? null : "T" + id.toString().substring(0, 8);
        return new Instrument(id, "Test", null, "EUR", assetClass, source, ref, null);
    }

    private static HistoryService serviceWith(Holding holding, Map<UUID, List<Quote>> quotesByInstrument) {
        return serviceWith(List.of(holding), quotesByInstrument);
    }

    private static HistoryService serviceWith(List<Holding> holdings, Map<UUID, List<Quote>> quotesByInstrument) {
        List<Instrument> instruments = holdings.stream()
                .map(holding -> instrument(holding.instrumentId(), AssetClass.EQUITY, PriceSource.YAHOO))
                .toList();
        return serviceWith(holdings, quotesByInstrument, instruments);
    }

    private static HistoryService serviceWith(
            List<Holding> holdings, Map<UUID, List<Quote>> quotesByInstrument, List<Instrument> instruments) {
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

            @Override
            public List<Holding> findByInstrument(UUID instrumentId) {
                return List.of();
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

            @Override
            public Map<UUID, LocalDate> findFirstQuoteDates(Set<UUID> instrumentIds) {
                return Map.of();
            }

            @Override
            public Map<UUID, Quote> findLatestOnOrBefore(Set<UUID> instrumentIds, LocalDate day) {
                Map<UUID, Quote> result = new HashMap<>();
                for (UUID instrumentId : instrumentIds) {
                    quotesByInstrument.getOrDefault(instrumentId, List.of()).stream()
                            .filter(q -> !q.asOf().isAfter(day))
                            .max(Comparator.comparing(Quote::asOf))
                            .ifPresent(q -> result.put(instrumentId, q));
                }
                return result;
            }
        };
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return instruments;
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return instruments.stream()
                        .filter(instrument -> instrument.id().equals(id))
                        .findFirst();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };
        LoadSnapshotsPort loadSnapshots = (from, to) -> List.of();
        return new HistoryService(loadHoldings, loadInstruments, loadQuotes, loadSnapshots);
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

            @Override
            public List<Holding> findByInstrument(UUID instrumentId) {
                return List.of();
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

            @Override
            public Map<UUID, LocalDate> findFirstQuoteDates(Set<UUID> instrumentIds) {
                return Map.of();
            }

            @Override
            public Map<UUID, Quote> findLatestOnOrBefore(Set<UUID> instrumentIds, LocalDate day) {
                return Map.of();
            }
        };
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of();
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };
        LoadSnapshotsPort loadSnapshots = (from, to) -> snapshots.stream()
                .filter(snapshot ->
                        !snapshot.date().isBefore(from) && !snapshot.date().isAfter(to))
                .toList();
        return new HistoryService(loadHoldings, loadInstruments, loadQuotes, loadSnapshots);
    }
}
