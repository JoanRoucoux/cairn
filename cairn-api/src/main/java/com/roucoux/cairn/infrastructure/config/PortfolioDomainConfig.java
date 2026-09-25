package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ImportPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.domain.service.PerformanceService;
import com.roucoux.cairn.domain.service.PortfolioImportService;
import com.roucoux.cairn.domain.service.PortfolioService;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PortfolioDomainConfig {

    @Bean
    PortfolioService portfolioService(LoadHoldingsPort loadHoldings, ValueHoldingUseCase valueHolding, Clock clock) {
        return new PortfolioService(loadHoldings, valueHolding, clock);
    }

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
    GetPerformanceUseCase getPerformanceUseCase(
            GetPortfolioUseCase getPortfolio, LoadQuotesPort loadQuotes, Clock clock, ZoneId zone) {
        return new PerformanceService(getPortfolio, loadQuotes, clock, zone);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId zone(@Value("${app.zone}") String zone) {
        return ZoneId.of(zone);
    }
}
