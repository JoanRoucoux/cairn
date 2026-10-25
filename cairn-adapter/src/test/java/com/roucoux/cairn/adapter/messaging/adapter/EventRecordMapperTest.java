package com.roucoux.cairn.adapter.messaging.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.events.AssetClass;
import com.roucoux.cairn.events.EventMetadata;
import com.roucoux.cairn.events.PriceUpdatedEvent;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import com.roucoux.cairn.events.RefreshTrigger;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EventRecordMapperTest {

    private static final UUID NORTHWIND = UUID.fromString("3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-25T09:30:00Z");

    private static final EventMetadata METADATA = EventMetadata.newBuilder()
            .setId(UUID.fromString("0b9d4e2a-7c1f-4a3b-8d5e-6f7a8b9c0d1e"))
            .setOccurredAt(OCCURRED_AT)
            .setSource("cairn-api")
            .build();

    @Test
    void mapsEveryPriceUpdatedField() {
        Quote quote = new Quote(
                NORTHWIND,
                LocalDate.of(2026, 10, 23),
                new BigDecimal("2396.17"),
                "EUR",
                PriceSource.AMUNDI,
                Instant.parse("2026-10-23T17:00:00Z"));

        PriceUpdatedEvent record = EventRecordMapper.toRecord(new PriceUpdated(quote), METADATA);

        assertThat(record.getMetadata()).isEqualTo(METADATA);
        assertThat(record.getInstrumentId()).isEqualTo(NORTHWIND);
        assertThat(record.getAsOf()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(record.getPrice()).isEqualByComparingTo("2396.17");
        assertThat(record.getCurrency()).isEqualTo("EUR");
        assertThat(record.getPriceSource()).isEqualTo(com.roucoux.cairn.events.PriceSource.AMUNDI);
    }

    @ParameterizedTest
    @CsvSource({
        "2396.17,2396.170000000000",
        "1E+3,1000.000000000000",
        "0.0000000000005,0.000000000001",
        "0.000000120000,0.000000120000"
    })
    void rescalesThePriceTo12DecimalsHalfUp(String price, String expected) {
        Quote quote = new Quote(
                NORTHWIND,
                LocalDate.of(2026, 10, 23),
                new BigDecimal(price),
                "EUR",
                PriceSource.COINGECKO,
                Instant.parse("2026-10-23T17:00:00Z"));

        PriceUpdatedEvent record = EventRecordMapper.toRecord(new PriceUpdated(quote), METADATA);

        assertThat(record.getPrice()).isEqualTo(new BigDecimal(expected));
        assertThat(record.getPrice().scale()).isEqualTo(12);
    }

    @Test
    void mapsRefreshCompletedFields() {
        RefreshCompletedEvent record = EventRecordMapper.toRecord(
                new RefreshCompleted(
                        Set.of(com.roucoux.cairn.domain.model.AssetClass.ETF),
                        7,
                        1,
                        com.roucoux.cairn.domain.model.event.RefreshTrigger.MANUAL),
                METADATA);

        assertThat(record.getMetadata()).isEqualTo(METADATA);
        assertThat(record.getAssetClasses()).containsExactly(AssetClass.ETF);
        assertThat(record.getRefreshed()).isEqualTo(7);
        assertThat(record.getFailed()).isEqualTo(1);
        assertThat(record.getTrigger()).isEqualTo(RefreshTrigger.MANUAL);
    }

    @Test
    void mapsAnEmptyAssetClassSetToAnEmptyArray() {
        RefreshCompletedEvent record = EventRecordMapper.toRecord(
                new RefreshCompleted(Set.of(), 0, 0, com.roucoux.cairn.domain.model.event.RefreshTrigger.SCHEDULER),
                METADATA);

        assertThat(record.getAssetClasses()).isEmpty();
    }

    @Test
    void everyDomainEnumConstantHasAnAvroSymbol() {
        for (com.roucoux.cairn.domain.model.AssetClass constant : com.roucoux.cairn.domain.model.AssetClass.values()) {
            assertThat(AssetClass.valueOf(constant.name())).isNotNull();
        }
        for (PriceSource constant : PriceSource.values()) {
            assertThat(com.roucoux.cairn.events.PriceSource.valueOf(constant.name()))
                    .isNotNull();
        }
        for (com.roucoux.cairn.domain.model.event.RefreshTrigger constant :
                com.roucoux.cairn.domain.model.event.RefreshTrigger.values()) {
            assertThat(RefreshTrigger.valueOf(constant.name())).isNotNull();
        }
    }
}
