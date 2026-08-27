package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
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
    PortfolioService portfolioService(
            LoadHoldingsPort loadHoldings,
            LoadInstrumentsPort loadInstruments,
            LoadAccountsPort loadAccounts,
            LoadQuotesPort loadQuotes,
            Clock clock) {
        return new PortfolioService(loadHoldings, loadInstruments, loadAccounts, loadQuotes, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
