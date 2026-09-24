package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
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
    private final AnnounceQuotesUseCase announce;

    public QuoteRefreshService(
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            SaveQuotePort saveQuote,
            RecordQuoteFailurePort recordFailure,
            AnnounceQuotesUseCase announce) {
        this.fetchers = List.copyOf(fetchers);
        this.loadInstruments = loadInstruments;
        this.saveQuote = saveQuote;
        this.recordFailure = recordFailure;
        this.announce = announce;
    }

    @Override
    public Quote refresh(Instrument instrument) {
        return fetcherFor(instrument.priceSource()).fetch(instrument);
    }

    @Override
    public RefreshReport refreshAll(Set<AssetClass> assetClasses, RefreshTrigger trigger) {
        int refreshed = 0;
        int skipped = 0;
        List<RefreshReport.Failure> failures = new ArrayList<>();

        for (Instrument instrument : loadInstruments.findRefreshable(assetClasses)) {
            if (!instrument.isRefreshable()) {
                skipped++;
                continue;
            }
            try {
                Quote quote = refresh(instrument);
                saveQuote.upsert(quote);
                refreshed++;
                announce.quotesSaved(List.of(quote));
            } catch (RuntimeException failure) {
                // Deliberately every runtime failure, not only the expected one: a single provider
                // answering something nobody foresaw must not leave the other instruments unpriced.
                if (loadInstruments.findById(instrument.id()).isEmpty()) {
                    // Deleted since it was read: a failure recorded against it would break the
                    // same foreign key as the quote did, and escape this catch.
                    continue;
                }
                String reason = reasonOf(failure);
                recordFailure.record(instrument.id(), instrument.priceSource(), reason);
                failures.add(new RefreshReport.Failure(
                        instrument.id(), instrument.name(), instrument.priceSource(), reason));
            }
        }
        announce.refreshCompleted(assetClasses, refreshed, failures.size(), trigger);
        return new RefreshReport(refreshed, skipped, List.copyOf(failures));
    }

    /** A NullPointerException carries no message, and "null" as a reason tells the reader nothing. */
    private static String reasonOf(RuntimeException failure) {
        String message = failure.getMessage();

        return message == null ? failure.getClass().getSimpleName() : message;
    }

    private FetchQuotePort fetcherFor(PriceSource source) {
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports(source))
                .findFirst()
                .orElseThrow(() -> new MarketDataUnavailableException("no adapter supports source " + source));
    }
}
