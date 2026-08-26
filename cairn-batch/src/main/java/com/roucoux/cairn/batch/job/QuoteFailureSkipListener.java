package com.roucoux.cairn.batch.job;

import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/** Records a skipped instrument through the outbound port, so the interface can flag it as stale. */
@Component
class QuoteFailureSkipListener implements SkipListener<Instrument, Quote> {

    private final RecordQuoteFailurePort recordFailure;

    QuoteFailureSkipListener(RecordQuoteFailurePort recordFailure) {
        this.recordFailure = recordFailure;
    }

    @Override
    public void onSkipInProcess(Instrument instrument, Throwable failure) {
        recordFailure.record(instrument.id(), instrument.priceSource(), failure.getMessage());
    }
}
