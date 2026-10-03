package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import com.roucoux.cairn.domain.service.QuoteAnnouncementService;
import com.roucoux.cairn.domain.service.QuoteRecordingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class QuoteDomainConfig {

    @Bean
    AnnounceQuotesUseCase announceQuotes(PublishEventPort publishEvent) {
        return new QuoteAnnouncementService(publishEvent);
    }

    @Bean
    QuoteRecordingService quoteRecordingService(
            LoadInstrumentsPort loadInstruments, SaveQuotePort saveQuote, AnnounceQuotesUseCase announceQuotes) {
        return new QuoteRecordingService(loadInstruments, saveQuote, announceQuotes);
    }
}
