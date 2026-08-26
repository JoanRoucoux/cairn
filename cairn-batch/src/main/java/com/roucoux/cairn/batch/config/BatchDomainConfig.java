package com.roucoux.cairn.batch.config;

import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.MarketDataPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import com.roucoux.cairn.domain.service.QuoteRefreshService;
import com.roucoux.cairn.domain.service.RevaluePositionService;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the batch: the domain service is a plain Java class, wired here against the
 * ports implemented by the adapters — the same wiring the API module does, for the same hexagon.
 */
@Configuration(proxyBeanMethods = false)
class BatchDomainConfig {

    @Bean
    RevaluePositionService revaluePositionService(MarketDataPort marketDataPort) {
        return new RevaluePositionService(marketDataPort);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The adapter is request-scoped for the API, where request-lifetime caching keeps its single
     * grouped call fresh across a call. The batch has no request scope, so its target bean
     * definition is switched to step-scoped instead — by bean name only, since {@code cairn-batch}
     * depends on {@code cairn-adapter} at runtime scope and may not reference the class at compile
     * time (see task 14 for the caching reasoning).
     */
    @Bean
    static BeanFactoryPostProcessor coinGeckoQuoteAdapterStepScoped() {
        return BatchDomainConfig::rescopeCoinGeckoQuoteAdapterToStep;
    }

    private static void rescopeCoinGeckoQuoteAdapterToStep(ConfigurableListableBeanFactory beanFactory) {
        String targetBeanName = "scopedTarget.coinGeckoQuoteAdapter";
        if (beanFactory.containsBeanDefinition(targetBeanName)) {
            BeanDefinition target = beanFactory.getBeanDefinition(targetBeanName);
            target.setScope("step");
        }
    }

    @Bean
    RefreshQuotesUseCase refreshQuotes(
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            SaveQuotePort saveQuote,
            RecordQuoteFailurePort recordFailure) {
        return new QuoteRefreshService(fetchers, loadInstruments, saveQuote, recordFailure);
    }
}
