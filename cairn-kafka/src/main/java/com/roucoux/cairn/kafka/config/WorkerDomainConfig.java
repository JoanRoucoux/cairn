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
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
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

    private static final String COIN_GECKO_BEAN_NAME = "coinGeckoQuoteAdapter";

    /**
     * A scoped proxy left over a prototype target re-resolves that target through {@code getBean}
     * on every method call, so keeping the proxy while only rescoping its target would still hand
     * out a new instance on every {@code supports()}/{@code fetch()} call, losing the one grouped
     * CoinGecko call a refresh run is meant to make. This drops the proxy entirely instead.
     */
    @Bean
    static BeanFactoryPostProcessor coinGeckoQuoteAdapterPrototypeScoped() {
        return WorkerDomainConfig::rescopeCoinGeckoQuoteAdapterToPrototype;
    }

    private static void rescopeCoinGeckoQuoteAdapterToPrototype(ConfigurableListableBeanFactory beanFactory) {
        String targetBeanName = ScopedProxyUtils.getTargetBeanName(COIN_GECKO_BEAN_NAME);
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)
                || !registry.containsBeanDefinition(targetBeanName)) {
            return;
        }
        String targetClassName = registry.getBeanDefinition(targetBeanName).getBeanClassName();
        GenericBeanDefinition prototypeDefinition = new GenericBeanDefinition();
        prototypeDefinition.setBeanClassName(targetClassName);
        prototypeDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        registry.removeBeanDefinition(COIN_GECKO_BEAN_NAME);
        registry.removeBeanDefinition(targetBeanName);
        registry.registerBeanDefinition(COIN_GECKO_BEAN_NAME, prototypeDefinition);
    }

    @Bean
    AnnounceQuotesUseCase announceQuotes(PublishEventPort publishEvent) {
        return new QuoteAnnouncementService(publishEvent);
    }

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
