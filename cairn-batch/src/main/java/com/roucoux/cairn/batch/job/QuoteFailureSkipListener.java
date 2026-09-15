package com.roucoux.cairn.batch.job;

import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/** Records a skipped instrument through the outbound port, so the interface can flag it as stale. */
@Component
class QuoteFailureSkipListener implements SkipListener<Instrument, Quote> {

    private final RecordQuoteFailurePort recordFailure;
    private final LoadInstrumentsPort loadInstruments;

    QuoteFailureSkipListener(RecordQuoteFailurePort recordFailure, LoadInstrumentsPort loadInstruments) {
        this.recordFailure = recordFailure;
        this.loadInstruments = loadInstruments;
    }

    @Override
    public void onSkipInProcess(Instrument instrument, Throwable failure) {
        recordFailure.record(instrument.id(), instrument.priceSource(), reasonOf(failure));
    }

    /**
     * The usual cause is an instrument deleted since it was read, and a failure recorded against it
     * would break the same foreign key and fail the step this skip just saved.
     */
    @Override
    public void onSkipInWrite(Quote quote, Throwable failure) {
        loadInstruments.findById(quote.instrumentId()).ifPresent(instrument -> onSkipInProcess(instrument, failure));
    }

    private static String reasonOf(Throwable failure) {
        String message = failure.getMessage();

        return message == null ? failure.getClass().getSimpleName() : message;
    }
}
