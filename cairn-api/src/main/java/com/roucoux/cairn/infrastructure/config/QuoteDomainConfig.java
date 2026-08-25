package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.out.MarketDataPort;
import com.roucoux.cairn.domain.service.QuoteService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the quote slice: the domain service is a plain Java class, wired here
 * against the ports implemented by the adapters. One configuration per slice, so a slice can be
 * removed by deleting files rather than editing them.
 */
@Configuration(proxyBeanMethods = false)
class QuoteDomainConfig {

    @Bean
    QuoteService quoteService(MarketDataPort marketDataPort) {
        return new QuoteService(marketDataPort);
    }
}
