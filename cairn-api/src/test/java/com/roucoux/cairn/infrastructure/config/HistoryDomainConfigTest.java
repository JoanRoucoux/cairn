package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.HistoryPoint;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.GetHistoryUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.LoadSnapshotsPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryDomainConfigTest {

    private static final UUID CASH_ID = UUID.randomUUID();
    private static final Instrument EUROS =
            new Instrument(CASH_ID, "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);

    @Test
    void wiresTheServiceSoACashHoldingCountsAtParInTheConstantMixHistory() {
        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
            @Override
            public List<Holding> findAll() {
                return List.of(
                        new Holding(UUID.randomUUID(), UUID.randomUUID(), CASH_ID, new BigDecimal("1000"), null));
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
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of(EUROS);
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return EUROS.id().equals(id) ? Optional.of(EUROS) : Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
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
            public Map<UUID, Quote> findLatestOnOrBefore(Set<UUID> instrumentIds, LocalDate day) {
                return Map.of();
            }
        };
        LoadSnapshotsPort loadSnapshots = (from, to) -> List.of();

        GetHistoryUseCase useCase =
                new HistoryDomainConfig().historyService(loadHoldings, loadInstruments, loadQuotes, loadSnapshots);
        List<HistoryPoint> series =
                useCase.history(HistoryMode.CONSTANT_MIX, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));

        assertThat(series)
                .extracting(HistoryPoint::totalEur)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1000"), new BigDecimal("1000"));
    }
}
