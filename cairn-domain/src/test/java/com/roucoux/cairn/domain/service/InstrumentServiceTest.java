package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.DeleteInstrumentPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstrumentServiceTest {

    @Test
    void createsAnInstrument() {
        Fixture fixture = Fixture.empty();

        Instrument created = fixture.service()
                .create("Bitcoin", null, "EUR", AssetClass.CRYPTO, PriceSource.COINGECKO, "bitcoin", null);

        assertThat(created.name()).isEqualTo("Bitcoin");
        assertThat(created.sourceRef()).isEqualTo("bitcoin");
    }

    @Test
    void updatesAnInstrumentPreservingItsCurrency() {
        Fixture fixture = Fixture.withExistingInstrument();

        Instrument updated = fixture.service()
                .update(
                        fixture.instrumentId(),
                        "Amundi ETF PEA S&P 500",
                        "FR0011550185",
                        AssetClass.ETF,
                        PriceSource.YAHOO,
                        "ETF3.PA",
                        "Updated description");

        assertThat(updated.currency()).isEqualTo("EUR");
        assertThat(updated.name()).isEqualTo("Amundi ETF PEA S&P 500");
        assertThat(updated.sourceRef()).isEqualTo("ETF3.PA");
    }

    @Test
    void rejectsUpdatingAnUnknownInstrument() {
        Fixture fixture = Fixture.withExistingInstrument();

        assertThatThrownBy(() -> fixture.service()
                        .update(UUID.randomUUID(), "Bitcoin", null, AssetClass.CRYPTO, PriceSource.MANUAL, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletesAnInstrumentWithNoHoldings() {
        Fixture fixture = Fixture.withExistingInstrument();

        fixture.service().delete(fixture.instrumentId());

        assertThat(fixture.deletedInstruments()).containsExactly(fixture.instrumentId());
        assertThat(fixture.deletedHoldings()).isEmpty();
    }

    @Test
    void deletesEveryHoldingOfTheInstrumentBeforeTheInstrumentItself() {
        Fixture fixture = Fixture.withExistingInstrumentAndHoldings();

        fixture.service().delete(fixture.instrumentId());

        assertThat(fixture.deletedHoldings()).containsExactlyInAnyOrderElementsOf(fixture.holdingIds());
        assertThat(fixture.deletedInstruments()).containsExactly(fixture.instrumentId());
    }

    @Test
    void rejectsDeletingAnUnknownInstrument() {
        Fixture fixture = Fixture.withExistingInstrument();

        assertThatThrownBy(() -> fixture.service().delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    private static final class Fixture {

        private final Map<UUID, Instrument> instruments;
        private final List<Holding> holdings;
        private final List<UUID> deletedHoldingIds = new ArrayList<>();
        private final List<UUID> deletedInstrumentIds = new ArrayList<>();
        private final UUID instrumentId;
        private final List<UUID> holdingIds;

        private Fixture(Map<UUID, Instrument> instruments, List<Holding> holdings, UUID instrumentId) {
            this.instruments = instruments;
            this.holdings = holdings;
            this.instrumentId = instrumentId;
            this.holdingIds = holdings.stream().map(Holding::id).toList();
        }

        static Fixture empty() {
            return new Fixture(new HashMap<>(), new ArrayList<>(), null);
        }

        static Fixture withExistingInstrument() {
            UUID id = UUID.randomUUID();
            Map<UUID, Instrument> instruments = new HashMap<>();
            instruments.put(
                    id,
                    new Instrument(id, "ETF", "FR0011871128", "EUR", AssetClass.ETF, PriceSource.MANUAL, null, null));
            return new Fixture(instruments, new ArrayList<>(), id);
        }

        static Fixture withExistingInstrumentAndHoldings() {
            Fixture fixture = withExistingInstrument();
            fixture.holdings.add(
                    new Holding(UUID.randomUUID(), UUID.randomUUID(), fixture.instrumentId, BigDecimal.ONE, null));
            fixture.holdings.add(
                    new Holding(UUID.randomUUID(), UUID.randomUUID(), fixture.instrumentId, BigDecimal.TEN, null));
            return new Fixture(fixture.instruments, fixture.holdings, fixture.instrumentId);
        }

        UUID instrumentId() {
            return instrumentId;
        }

        List<UUID> holdingIds() {
            return holdingIds;
        }

        List<UUID> deletedHoldings() {
            return deletedHoldingIds;
        }

        List<UUID> deletedInstruments() {
            return deletedInstrumentIds;
        }

        InstrumentService service() {
            return new InstrumentService(
                    new InMemoryLoadInstrumentsPort(),
                    new InMemorySaveInstrumentPort(),
                    new InMemoryDeleteInstrumentPort(),
                    new InMemoryLoadHoldingsPort(),
                    new InMemoryDeleteHoldingPort());
        }

        private final class InMemoryLoadInstrumentsPort implements LoadInstrumentsPort {
            @Override
            public List<Instrument> findAll() {
                return List.copyOf(instruments.values());
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return Optional.ofNullable(instruments.get(id));
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        }

        private final class InMemorySaveInstrumentPort implements SaveInstrumentPort {
            @Override
            public Instrument save(Instrument instrument) {
                instruments.put(instrument.id(), instrument);
                return instrument;
            }
        }

        private final class InMemoryDeleteInstrumentPort implements DeleteInstrumentPort {
            @Override
            public void delete(UUID id) {
                instruments.remove(id);
                deletedInstrumentIds.add(id);
            }
        }

        private final class InMemoryLoadHoldingsPort implements LoadHoldingsPort {
            @Override
            public List<Holding> findAll() {
                return List.copyOf(holdings);
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
                return holdings.stream()
                        .filter(h -> h.instrumentId().equals(instrumentId))
                        .toList();
            }
        }

        private final class InMemoryDeleteHoldingPort implements DeleteHoldingPort {
            @Override
            public void delete(UUID id) {
                holdings.removeIf(h -> h.id().equals(id));
                deletedHoldingIds.add(id);
            }
        }
    }
}
