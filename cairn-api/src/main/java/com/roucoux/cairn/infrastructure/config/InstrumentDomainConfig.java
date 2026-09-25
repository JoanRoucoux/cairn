package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.DeleteInstrumentPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.domain.service.InstrumentResolutionService;
import com.roucoux.cairn.domain.service.InstrumentService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InstrumentDomainConfig {

    @Bean
    InstrumentResolutionService instrumentResolutionService(List<ResolveInstrumentPort> resolvers) {
        return new InstrumentResolutionService(resolvers);
    }

    @Bean
    InstrumentService instrumentService(
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            DeleteInstrumentPort deleteInstrument,
            LoadHoldingsPort loadHoldings,
            DeleteHoldingPort deleteHolding) {
        return new InstrumentService(loadInstruments, saveInstrument, deleteInstrument, loadHoldings, deleteHolding);
    }
}
