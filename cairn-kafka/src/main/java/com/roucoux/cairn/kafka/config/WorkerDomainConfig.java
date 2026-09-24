package com.roucoux.cairn.kafka.config;

import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.RecordValuationUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import com.roucoux.cairn.domain.port.out.SaveValuationPort;
import com.roucoux.cairn.domain.service.HoldingValuationService;
import com.roucoux.cairn.domain.service.PortfolioService;
import com.roucoux.cairn.domain.service.ValuationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the worker: the same portfolio-valuation slice as the API's
 * {@code PortfolioDomainConfig}/{@code HoldingDomainConfig}, wired here against the ports
 * implemented by the adapters, plus the valuation slice the worker alone runs.
 */
@Configuration(proxyBeanMethods = false)
class WorkerDomainConfig {

    @Bean
    ValueHoldingUseCase valueHoldingUseCase(
            LoadInstrumentsPort loadInstruments,
            LoadAccountsPort loadAccounts,
            LoadQuotesPort loadQuotes,
            Clock clock) {
        return new HoldingValuationService(loadInstruments, loadAccounts, loadQuotes, clock);
    }

    @Bean
    GetPortfolioUseCase getPortfolioUseCase(
            LoadHoldingsPort loadHoldings, ValueHoldingUseCase valueHolding, Clock clock) {
        return new PortfolioService(loadHoldings, valueHolding, clock);
    }

    @Bean
    RecordValuationUseCase recordValuationUseCase(
            GetPortfolioUseCase getPortfolio, SaveValuationPort saveValuation, PublishEventPort publishEvent) {
        return new ValuationService(getPortfolio, saveValuation, publishEvent);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
