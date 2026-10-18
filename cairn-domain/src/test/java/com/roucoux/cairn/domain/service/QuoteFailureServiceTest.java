package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteFailureServiceTest {

    @Test
    void recordsThroughThePort() {
        RecordingPort port = new RecordingPort();
        UUID instrumentId = UUID.randomUUID();

        new QuoteFailureService(port).record(instrumentId, PriceSource.YAHOO, "boom");

        assertThat(port.recorded).containsExactly(instrumentId);
    }

    @Test
    void purgesFailuresOlderThanNinetyDays() {
        RecordingPort port = new RecordingPort();
        Instant before = Instant.now();

        new QuoteFailureService(port).purgeExpired();

        assertThat(port.cutoffs)
                .singleElement()
                .satisfies(cutoff -> assertThat(cutoff)
                        .isBetween(
                                before.minus(Duration.ofDays(90)), Instant.now().minus(Duration.ofDays(90))));
    }

    private static final class RecordingPort implements RecordQuoteFailurePort {
        private final List<UUID> recorded = new ArrayList<>();
        private final List<Instant> cutoffs = new ArrayList<>();

        @Override
        public void record(UUID instrumentId, PriceSource source, String message) {
            recorded.add(instrumentId);
        }

        @Override
        public void deleteBefore(Instant cutoff) {
            cutoffs.add(cutoff);
        }
    }
}
