package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class QuoteRefreshService implements RefreshQuotesUseCase {

    private final List<FetchQuotePort> fetchers;
    private final LoadInstrumentsPort loadInstruments;
    private final SaveQuotePort saveQuote;
    private final RecordQuoteFailurePort recordFailure;

    public QuoteRefreshService(
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            SaveQuotePort saveQuote,
            RecordQuoteFailurePort recordFailure) {
        this.fetchers = List.copyOf(fetchers);
        this.loadInstruments = loadInstruments;
        this.saveQuote = saveQuote;
        this.recordFailure = recordFailure;
    }

    @Override
    public Quote refresh(Instrument instrument) {
        return fetcherFor(instrument.priceSource()).fetch(instrument);
    }

    @Override
    public RefreshReport refreshAll(Set<AssetClass> assetClasses) {
        int refreshed = 0;
        int skipped = 0;
        List<RefreshReport.Failure> failures = new ArrayList<>();

        for (Instrument instrument : loadInstruments.findRefreshable(assetClasses)) {
            if (!instrument.isRefreshable()) {
                skipped++;
                continue;
            }
            try {
                saveQuote.upsert(refresh(instrument));
                refreshed++;
            } catch (MarketDataUnavailableException failure) {
                recordFailure.record(instrument.id(), instrument.priceSource(), failure.getMessage());
                failures.add(new RefreshReport.Failure(
                        instrument.id(), instrument.name(), instrument.priceSource(), failure.getMessage()));
            }
        }
        return new RefreshReport(refreshed, skipped, List.copyOf(failures));
    }

    private FetchQuotePort fetcherFor(PriceSource source) {
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports(source))
                .findFirst()
                .orElseThrow(() -> new MarketDataUnavailableException("no adapter supports source " + source));
    }
}
