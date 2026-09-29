package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import com.roucoux.cairn.domain.service.QuoteRecordingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class QuoteDomainConfig {

    @Bean
    QuoteRecordingService quoteRecordingService(LoadInstrumentsPort loadInstruments, SaveQuotePort saveQuote) {
        return new QuoteRecordingService(loadInstruments, saveQuote);
    }
}
