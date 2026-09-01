package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.ImportPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.domain.service.PortfolioImportService;
import com.roucoux.cairn.domain.service.PortfolioService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the portfolio slice: the domain service is a plain Java class, wired here
 * against the ports implemented by the adapters. One configuration per slice, so a slice can be
 * removed by deleting files rather than editing them.
 */
@Configuration(proxyBeanMethods = false)
class PortfolioDomainConfig {

    @Bean
    PortfolioService portfolioService(LoadHoldingsPort loadHoldings, ValueHoldingUseCase valueHolding, Clock clock) {
        return new PortfolioService(loadHoldings, valueHolding, clock);
    }

    /**
     * Callers go through {@code PortfolioImportTransaction}, not straight to this bean: the
     * all-or-nothing guarantee needs a transaction the domain cannot open itself.
     */
    @Bean
    ImportPortfolioUseCase importPortfolioUseCase(
            LoadAccountsPort loadAccounts,
            SaveAccountPort saveAccount,
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            ResolveInstrumentUseCase resolveInstrument,
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding) {
        return new PortfolioImportService(
                loadAccounts,
                saveAccount,
                loadInstruments,
                saveInstrument,
                resolveInstrument,
                loadHoldings,
                saveHolding);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
