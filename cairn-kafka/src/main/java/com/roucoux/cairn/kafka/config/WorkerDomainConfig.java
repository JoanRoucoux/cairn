package com.roucoux.cairn.kafka.config;

import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.RecordValuationUseCase;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import com.roucoux.cairn.domain.port.out.SaveValuationPort;
import com.roucoux.cairn.domain.service.HoldingValuationService;
import com.roucoux.cairn.domain.service.PortfolioService;
import com.roucoux.cairn.domain.service.QuoteAnnouncementService;
import com.roucoux.cairn.domain.service.QuoteRefreshService;
import com.roucoux.cairn.domain.service.ValuationService;
import java.time.Clock;
import java.util.List;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Composition root of the worker: the same portfolio-valuation slice as the API's
 * {@code PortfolioDomainConfig}/{@code HoldingDomainConfig}, wired here against the ports
 * implemented by the adapters, plus the valuation slice the worker alone runs and the quote-refresh
 * slice the intraday scheduler drives.
 */
@Configuration(proxyBeanMethods = false)
class WorkerDomainConfig {

    /**
     * The adapter is request-scoped for the API, where request-lifetime caching keeps its single
     * grouped call fresh across a call. The worker has no request scope and no per-run scope of its
     * own either, so its target bean definition is switched to prototype instead — by bean name
     * only, since {@code cairn-kafka} depends on {@code cairn-adapter} at runtime scope and may not
     * reference the class at compile time (same technique as {@code BatchDomainConfig}).
     */
    @Bean
    static BeanFactoryPostProcessor coinGeckoQuoteAdapterPrototypeScoped() {
        return WorkerDomainConfig::rescopeCoinGeckoQuoteAdapterToPrototype;
    }

    private static void rescopeCoinGeckoQuoteAdapterToPrototype(ConfigurableListableBeanFactory beanFactory) {
        String targetBeanName = ScopedProxyUtils.getTargetBeanName("coinGeckoQuoteAdapter");
        if (beanFactory.containsBeanDefinition(targetBeanName)) {
            BeanDefinition target = beanFactory.getBeanDefinition(targetBeanName);
            target.setScope("prototype");
        }
    }

    @Bean
    AnnounceQuotesUseCase announceQuotes(PublishEventPort publishEvent) {
        return new QuoteAnnouncementService(publishEvent);
    }

    /** Prototype-scoped so the intraday scheduler's {@code ObjectProvider} gets a fresh instance,
     * and with it a fresh {@code CoinGeckoQuoteAdapter}, on every run. */
    @Bean
    @Scope("prototype")
    RefreshQuotesUseCase refreshQuotes(
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            SaveQuotePort saveQuote,
            RecordQuoteFailurePort recordFailure,
            AnnounceQuotesUseCase announceQuotes) {
        return new QuoteRefreshService(fetchers, loadInstruments, saveQuote, recordFailure, announceQuotes);
    }

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
