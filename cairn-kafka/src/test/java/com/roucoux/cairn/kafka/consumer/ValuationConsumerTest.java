package com.roucoux.cairn.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.IntradayValuation;
import com.roucoux.cairn.domain.port.in.RecordValuationUseCase;
import com.roucoux.cairn.kafka.RefreshCompletedEvents;
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
    void aRefreshCompletedEventRecordsOneValuation() {
        consumer.onRefreshCompleted(RefreshCompletedEvents.sample());

        assertThat(recordedAt).containsExactly(NOW);
    }

    @Test
    void anyOtherRecordRecordsNothing() {
        consumer.ignore("something else");
        consumer.ignore(null);

        assertThat(recordedAt).isEmpty();
    }
}
