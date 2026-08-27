package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import com.roucoux.cairn.domain.service.InstrumentResolutionService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the instrument-resolution slice: the domain service is a plain Java class,
 * wired here against every {@link ResolveInstrumentPort} the adapters expose. One configuration
 * per slice, so a slice can be removed by deleting files rather than editing them.
 */
@Configuration(proxyBeanMethods = false)
class InstrumentDomainConfig {

    @Bean
    InstrumentResolutionService instrumentResolutionService(List<ResolveInstrumentPort> resolvers) {
        return new InstrumentResolutionService(resolvers);
    }
}
