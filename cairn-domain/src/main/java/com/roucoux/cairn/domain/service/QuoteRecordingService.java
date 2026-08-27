package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.RecordManualQuoteUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class QuoteRecordingService implements RecordManualQuoteUseCase {

    private final LoadInstrumentsPort loadInstruments;
    private final SaveQuotePort saveQuote;

    public QuoteRecordingService(LoadInstrumentsPort loadInstruments, SaveQuotePort saveQuote) {
        this.loadInstruments = loadInstruments;
        this.saveQuote = saveQuote;
    }

    @Override
    public Quote record(UUID instrumentId, LocalDate asOf, BigDecimal price) {
        Instrument instrument = loadInstruments
                .findById(instrumentId)
                .orElseThrow(() -> new NotFoundException("instrument", instrumentId));
        Quote quote = new Quote(instrumentId, asOf, price, instrument.currency(), PriceSource.MANUAL, Instant.now());
        saveQuote.upsert(quote);
        return quote;
    }
}
