package com.roucoux.cairn.batch.config;

import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.in.BackfillQuotesUseCase;
import com.roucoux.cairn.domain.port.in.ComputeSnapshotUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
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
import com.roucoux.cairn.domain.port.out.SaveSnapshotPort;
import com.roucoux.cairn.domain.service.BackfillService;
import com.roucoux.cairn.domain.service.HoldingValuationService;
import com.roucoux.cairn.domain.service.PortfolioService;
import com.roucoux.cairn.domain.service.QuoteAnnouncementService;
import com.roucoux.cairn.domain.service.QuoteRefreshService;
import com.roucoux.cairn.domain.service.SnapshotService;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BatchDomainConfig {

    @Bean
    Clock clock(@Value("${app.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
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
    ComputeSnapshotUseCase computeSnapshotUseCase(
            GetPortfolioUseCase getPortfolio, SaveSnapshotPort saveSnapshot, Clock clock) {
        return new SnapshotService(getPortfolio, saveSnapshot, clock);
    }

    @Bean
    static BeanFactoryPostProcessor coinGeckoQuoteAdapterStepScoped() {
        return BatchDomainConfig::rescopeCoinGeckoQuoteAdapterToStep;
    }

    private static void rescopeCoinGeckoQuoteAdapterToStep(ConfigurableListableBeanFactory beanFactory) {
        String targetBeanName = ScopedProxyUtils.getTargetBeanName("coinGeckoQuoteAdapter");
        if (beanFactory.containsBeanDefinition(targetBeanName)) {
            BeanDefinition target = beanFactory.getBeanDefinition(targetBeanName);
            target.setScope("step");
        }
    }

    @Bean
    AnnounceQuotesUseCase announceQuotes(PublishEventPort publishEvent) {
        return new QuoteAnnouncementService(publishEvent);
    }

    @Bean
    RefreshQuotesUseCase refreshQuotes(
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            SaveQuotePort saveQuote,
            RecordQuoteFailurePort recordFailure,
            AnnounceQuotesUseCase announceQuotes) {
        return new QuoteRefreshService(fetchers, loadInstruments, saveQuote, recordFailure, announceQuotes);
    }

    @Bean
    BackfillQuotesUseCase backfillQuotes(List<FetchQuotePort> fetchers, SaveQuotePort saveQuote) {
        return new BackfillService(fetchers, saveQuote);
    }
}
