package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.BackfillQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.time.LocalDate;
import java.util.List;

public class BackfillService implements BackfillQuotesUseCase {

    private final List<FetchQuotePort> fetchers;
    private final SaveQuotePort saveQuote;

    public BackfillService(List<FetchQuotePort> fetchers, SaveQuotePort saveQuote) {
        this.fetchers = List.copyOf(fetchers);
        this.saveQuote = saveQuote;
    }

    @Override
    public int backfill(Instrument instrument, LocalDate from) {
        if (!instrument.isRefreshable()) {
            return 0;
        }
        List<Quote> history = fetchers.stream()
                .filter(fetcher -> fetcher.supports(instrument.priceSource()))
                .findFirst()
                .orElseThrow(() ->
                        new MarketDataUnavailableException("no adapter supports source " + instrument.priceSource()))
                .fetchHistory(instrument, from);
        saveQuote.upsertAll(history);
        return history.size();
    }
}
