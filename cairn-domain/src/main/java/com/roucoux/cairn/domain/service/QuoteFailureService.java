package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.RecordQuoteFailureUseCase;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class QuoteFailureService implements RecordQuoteFailureUseCase {

    private static final Duration RETENTION = Duration.ofDays(90);

    private final RecordQuoteFailurePort recordFailure;

    public QuoteFailureService(RecordQuoteFailurePort recordFailure) {
        this.recordFailure = recordFailure;
    }

    @Override
    public void record(UUID instrumentId, PriceSource source, String message) {
        recordFailure.record(instrumentId, source, message);
    }

    @Override
    public void purgeExpired() {
        recordFailure.deleteBefore(Instant.now().minus(RETENTION));
    }
}
