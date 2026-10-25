package com.roucoux.cairn.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EventRecordsRoundTripTest {

    private static final EventMetadata METADATA = EventMetadata.newBuilder()
            .setId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .setOccurredAt(Instant.parse("2026-09-24T10:15:30.123Z"))
            .setSource("northwind")
            .build();

    @BeforeAll
    static void trustTheEventClasses() {
        TrustedEventClasses.register();
    }

    @Test
    void aPriceUpdatedEventSurvivesBinaryEncoding() throws IOException {
        PriceUpdatedEvent event = PriceUpdatedEvent.newBuilder()
                .setMetadata(METADATA)
                .setInstrumentId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .setAsOf(LocalDate.of(2026, 9, 24))
                .setPrice(new BigDecimal("0.000000120000"))
                .setCurrency("EUR")
                .setPriceSource(PriceSource.COINGECKO)
                .build();

        PriceUpdatedEvent back = roundTrip(event, PriceUpdatedEvent.class);

        assertThat(back).isEqualTo(event);
        assertThat(back.getPrice().scale()).isEqualTo(12);
    }

    @Test
    void aRefreshCompletedEventWithNoAssetClassSurvivesBinaryEncoding() throws IOException {
        RefreshCompletedEvent event = RefreshCompletedEvent.newBuilder()
                .setMetadata(METADATA)
                .setAssetClasses(List.of())
                .setRefreshed(3)
                .setFailed(1)
                .setTrigger(RefreshTrigger.SCHEDULER)
                .build();

        assertThat(roundTrip(event, RefreshCompletedEvent.class)).isEqualTo(event);
    }

    private static <T extends SpecificRecord> T roundTrip(T event, Class<T> type) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        new SpecificDatumWriter<T>(type).write(event, encoder);
        encoder.flush();
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(out.toByteArray(), null);
        return new SpecificDatumReader<T>(type).read(null, decoder);
    }
}
