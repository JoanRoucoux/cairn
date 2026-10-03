package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.in.RecordManualQuoteUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class QuoteRecordingService implements RecordManualQuoteUseCase {

    private final LoadInstrumentsPort loadInstruments;
    private final SaveQuotePort saveQuote;
    private final AnnounceQuotesUseCase announceQuotes;

    public QuoteRecordingService(
            LoadInstrumentsPort loadInstruments, SaveQuotePort saveQuote, AnnounceQuotesUseCase announceQuotes) {
        this.loadInstruments = loadInstruments;
        this.saveQuote = saveQuote;
        this.announceQuotes = announceQuotes;
    }

    @Override
    public Quote record(UUID instrumentId, LocalDate asOf, BigDecimal price) {
        Instrument instrument = loadInstruments
                .findById(instrumentId)
                .orElseThrow(() -> new NotFoundException("instrument", instrumentId));
        Quote quote = new Quote(instrumentId, asOf, price, instrument.currency(), PriceSource.MANUAL, Instant.now());
        saveQuote.upsert(quote);
        announceQuotes.quotesSaved(List.of(quote));
        announceQuotes.refreshCompleted(Set.of(instrument.assetClass()), 1, 0, RefreshTrigger.MANUAL);
        return quote;
    }
}
