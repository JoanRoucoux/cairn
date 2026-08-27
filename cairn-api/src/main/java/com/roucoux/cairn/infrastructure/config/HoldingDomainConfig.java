package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.service.HoldingService;
import com.roucoux.cairn.domain.service.HoldingValuationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the holding slice: the domain service is a plain Java class, wired here
 * against the ports implemented by the adapters. One configuration per slice, so a slice can be
 * removed by deleting files rather than editing them.
 */
@Configuration(proxyBeanMethods = false)
class HoldingDomainConfig {

    @Bean
    HoldingService holdingService(
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding,
            DeleteHoldingPort deleteHolding,
            LoadAccountsPort loadAccounts,
            LoadInstrumentsPort loadInstruments) {
        return new HoldingService(loadHoldings, saveHolding, deleteHolding, loadAccounts, loadInstruments);
    }

    /**
     * Also consumed by the portfolio slice (via {@link ValueHoldingUseCase}) so both slices share
     * the exact same holding-to-{@code ValuedHolding} enrichment logic.
     */
    @Bean
    ValueHoldingUseCase valueHoldingUseCase(
            LoadInstrumentsPort loadInstruments, LoadAccountsPort loadAccounts, LoadQuotesPort loadQuotes) {
        return new HoldingValuationService(loadInstruments, loadAccounts, loadQuotes);
    }
}
