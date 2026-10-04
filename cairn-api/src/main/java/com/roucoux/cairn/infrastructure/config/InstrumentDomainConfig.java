package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import com.roucoux.cairn.domain.service.InstrumentResolutionService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InstrumentDomainConfig {

    @Bean
    InstrumentResolutionService instrumentResolutionService(
            List<ResolveInstrumentPort> resolvers, List<FetchQuotePort> fetchers, LoadInstrumentsPort loadInstruments) {
        return new InstrumentResolutionService(resolvers, fetchers, loadInstruments);
    }
}
