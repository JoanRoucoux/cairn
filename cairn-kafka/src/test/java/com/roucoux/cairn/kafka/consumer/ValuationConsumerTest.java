package com.roucoux.cairn.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.IntradayValuation;
import com.roucoux.cairn.domain.port.in.RecordValuationUseCase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValuationConsumerTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:31:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final List<Instant> recordedAt = new ArrayList<>();
    private final RecordValuationUseCase recordValuation = at -> {
        recordedAt.add(at);
        return new IntradayValuation(at, BigDecimal.ZERO);
    };
    private final ValuationConsumer consumer = new ValuationConsumer(recordValuation, CLOCK);

    @Test
    void aRefreshCompletedEnvelopeRecordsOneValuationAtNow() {
        consumer.onMessage(envelope("refresh.completed"));

        assertThat(recordedAt).containsExactly(NOW);
    }

    @Test
    void aPriceUpdatedEnvelopeIsIgnored() {
        consumer.onMessage(envelope("price.updated"));

        assertThat(recordedAt).isEmpty();
    }

    @Test
    void itsOwnValuationRecordedEnvelopeIsIgnored() {
        consumer.onMessage(envelope("valuation.recorded"));

        assertThat(recordedAt).isEmpty();
    }

    @Test
    void anUnknownTypeIsIgnored() {
        consumer.onMessage(envelope("something.else"));

        assertThat(recordedAt).isEmpty();
    }

    @Test
    void aMalformedMessageIsLoggedAndSkippedRatherThanBlockingThePartition() {
        consumer.onMessage("not json at all");

        assertThat(recordedAt).isEmpty();
    }

    private static String envelope(String type) {
        return """
                {"id":"11111111-1111-1111-1111-111111111111","type":"%s","version":1,\
                "occurredAt":"2026-09-24T09:31:00Z","source":"cairn-api","data":{}}\
                """.formatted(type);
    }
}
