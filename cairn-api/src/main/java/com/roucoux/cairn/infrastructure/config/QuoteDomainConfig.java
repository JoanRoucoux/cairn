package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import com.roucoux.cairn.domain.service.QuoteAnnouncementService;
import com.roucoux.cairn.domain.service.QuoteRecordingService;
import com.roucoux.cairn.domain.service.QuoteRefreshService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class QuoteDomainConfig {

    @Bean
    QuoteRecordingService quoteRecordingService(LoadInstrumentsPort loadInstruments, SaveQuotePort saveQuote) {
        return new QuoteRecordingService(loadInstruments, saveQuote);
    }

    @Bean
    AnnounceQuotesUseCase announceQuotes(PublishEventPort publishEvent) {
        return new QuoteAnnouncementService(publishEvent);
    }

    @Bean
    RefreshQuotesUseCase refreshQuotes(
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            SaveQuotePort saveQuote,
            RecordQuoteFailurePort recordFailure,
            AnnounceQuotesUseCase announceQuotes) {
        return new QuoteRefreshService(fetchers, loadInstruments, saveQuote, recordFailure, announceQuotes);
    }
}
