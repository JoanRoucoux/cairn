package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.LoadSnapshotsPort;
import com.roucoux.cairn.domain.port.out.LoadValuationsPort;
import com.roucoux.cairn.domain.service.HistoryService;
import com.roucoux.cairn.domain.service.IntradayHistoryService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the history slice: the domain service is a plain Java class, wired here
 * against the ports implemented by the adapters. One configuration per slice, so a slice can be
 * removed by deleting files rather than editing them.
 */
@Configuration(proxyBeanMethods = false)
class HistoryDomainConfig {

    @Bean
    HistoryService historyService(
            LoadHoldingsPort loadHoldings,
            LoadInstrumentsPort loadInstruments,
            LoadQuotesPort loadQuotes,
            LoadSnapshotsPort loadSnapshots) {
        return new HistoryService(loadHoldings, loadInstruments, loadQuotes, loadSnapshots);
    }

    @Bean
    IntradayHistoryService intradayHistoryService(
            GetPortfolioUseCase getPortfolio, LoadValuationsPort loadValuations, Clock clock) {
        return new IntradayHistoryService(getPortfolio, loadValuations, clock);
    }
}
