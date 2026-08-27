package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import com.roucoux.cairn.domain.service.QuoteRecordingService;
import com.roucoux.cairn.domain.service.QuoteRefreshService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the quote slice: the domain services are plain Java classes, wired here
 * against the ports they need — the same wiring the batch module does, for the same hexagon. One
 * configuration per slice, so a slice can be removed by deleting files rather than editing them.
 */
@Configuration(proxyBeanMethods = false)
class QuoteDomainConfig {

    @Bean
    QuoteRecordingService quoteRecordingService(LoadInstrumentsPort loadInstruments, SaveQuotePort saveQuote) {
        return new QuoteRecordingService(loadInstruments, saveQuote);
    }

    @Bean
    RefreshQuotesUseCase refreshQuotes(
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            SaveQuotePort saveQuote,
            RecordQuoteFailurePort recordFailure) {
        return new QuoteRefreshService(fetchers, loadInstruments, saveQuote, recordFailure);
    }
}
