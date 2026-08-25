package com.roucoux.cairn.batch.config;

import com.roucoux.cairn.domain.port.out.MarketDataPort;
import com.roucoux.cairn.domain.service.RevaluePositionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the batch: the domain service is a plain Java class, wired here against the
 * ports implemented by the adapters — the same wiring the API module does, for the same hexagon.
 */
@Configuration(proxyBeanMethods = false)
class BatchDomainConfig {

    @Bean
    RevaluePositionService revaluePositionService(MarketDataPort marketDataPort) {
        return new RevaluePositionService(marketDataPort);
    }
}
