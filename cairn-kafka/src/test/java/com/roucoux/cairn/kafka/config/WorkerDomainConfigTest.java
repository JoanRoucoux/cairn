package com.roucoux.cairn.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.IntradayValuation;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.DomainEvent;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/** Bean-wiring test, no Spring context: calls the {@code @Bean} methods directly. */
class WorkerDomainConfigTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:31:42Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final WorkerDomainConfig config = new WorkerDomainConfig();

    private final LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
        @Override
        public List<Holding> findAll() {
            return List.of();
        }

        @Override
        public Optional<Holding> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
            return Optional.empty();
        }

        @Override
        public List<Holding> findByInstrument(UUID instrumentId) {
            return List.of();
        }
    };

    private final LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
        @Override
        public List<Instrument> findAll() {
            return List.of();
        }

        @Override
        public Optional<Instrument> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
            return List.of();
        }
    };

    private final LoadAccountsPort loadAccounts = new LoadAccountsPort() {
        @Override
        public List<Account> findAll() {
            return List.of();
        }

        @Override
        public Optional<Account> findById(UUID id) {
            return Optional.empty();
        }
    };

    private final LoadQuotesPort loadQuotes = new LoadQuotesPort() {
        @Override
        public Optional<Quote> findLatest(UUID instrumentId) {
            return Optional.empty();
        }

        @Override
        public Optional<Quote> findPrevious(UUID instrumentId, LocalDate before) {
            return Optional.empty();
        }

        @Override
        public List<Quote> findBetween(UUID instrumentId, LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override
        public Map<UUID, List<Quote>> findBetweenForAll(Set<UUID> instrumentIds, LocalDate from, LocalDate to) {
            return Map.of();
        }

        @Override
        public Map<UUID, Quote> findLatestOnOrBefore(Set<UUID> instrumentIds, LocalDate day) {
            return Map.of();
        }

        @Override
        public Map<UUID, LocalDate> findFirstQuoteDates(Set<UUID> instrumentIds) {
            return Map.of();
        }
    };

    @Test
    void wiresTheHoldingSlicesSoAnEmptyPortfolioTotalsToZero() {
        ValueHoldingUseCase valueHolding = config.valueHoldingUseCase(loadInstruments, loadAccounts, loadQuotes, CLOCK);
        GetPortfolioUseCase getPortfolio = config.getPortfolioUseCase(loadHoldings, valueHolding, CLOCK);

        Portfolio portfolio = getPortfolio.get();

        assertThat(portfolio.total().amount()).isEqualByComparingTo("0");
    }

    @Test
    void wiresTheValuationSliceSoRecordingSavesAndPublishes() {
        ValueHoldingUseCase valueHolding = config.valueHoldingUseCase(loadInstruments, loadAccounts, loadQuotes, CLOCK);
        GetPortfolioUseCase getPortfolio = config.getPortfolioUseCase(loadHoldings, valueHolding, CLOCK);
        List<IntradayValuation> saved = new ArrayList<>();
        List<DomainEvent> published = new ArrayList<>();
        SaveValuationPort saveValuation = new SaveValuationPort() {
            @Override
            public void upsert(IntradayValuation valuation) {
                saved.add(valuation);
            }

            @Override
            public void deleteBefore(Instant cutoff) {}
        };
        PublishEventPort publishEvent = published::add;

        RecordValuationUseCase recordValuation =
                config.recordValuationUseCase(getPortfolio, saveValuation, publishEvent);
        recordValuation.record(NOW);

        assertThat(saved).hasSize(1);
        assertThat(published).hasSize(1);
    }

    @Test
    void exposesASystemClock() {
        assertThat(config.clock()).isNotNull();
    }

    @Test
    void wiresTheRefreshSliceSoARefreshAnnouncesItsEnd() {
        List<Quote> saved = new ArrayList<>();
        List<DomainEvent> published = new ArrayList<>();
        FetchQuotePort fetcher = new FetchQuotePort() {
            @Override
            public boolean supports(com.roucoux.cairn.domain.model.PriceSource source) {
                return true;
            }

            @Override
            public Quote fetch(Instrument instrument) {
                throw new MarketDataUnavailableException("unused");
            }

            @Override
            public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
                return List.of();
            }
        };
        SaveQuotePort saveQuote = new SaveQuotePort() {
            @Override
            public void upsert(Quote quote) {
                saved.add(quote);
            }

            @Override
            public void upsertAll(List<Quote> quotes) {
                saved.addAll(quotes);
            }
        };
        RecordQuoteFailurePort recordFailure = (instrumentId, source, message) -> {};
        AnnounceQuotesUseCase announceQuotes = config.announceQuotes(published::add);

        RefreshQuotesUseCase refreshQuotes =
                config.refreshQuotes(List.of(fetcher), loadInstruments, saveQuote, recordFailure, announceQuotes);
        refreshQuotes.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER);

        assertThat(saved).isEmpty();
        assertThat(published).hasSize(1);
    }

    @Component("coinGeckoQuoteAdapter")
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    static class RequestScopedStandIn {}

    @Component
    @Scope("prototype")
    static class PrototypeConsumer {
        private final RequestScopedStandIn adapter;

        PrototypeConsumer(RequestScopedStandIn adapter) {
            this.adapter = adapter;
        }

        RequestScopedStandIn adapter() {
            return adapter;
        }
    }

    @Test
    void replacesTheScopedProxySoEachConsumerConstructionGetsItsOwnPlainInstance() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(RequestScopedStandIn.class, PrototypeConsumer.class);
            String targetBeanName = ScopedProxyUtils.getTargetBeanName("coinGeckoQuoteAdapter");
            assertThat(context.getBeanFactory()
                            .getBeanDefinition(targetBeanName)
                            .getScope())
                    .isEqualTo("request");

            BeanFactoryPostProcessor processor = WorkerDomainConfig.coinGeckoQuoteAdapterPrototypeScoped();
            processor.postProcessBeanFactory(context.getBeanFactory());
            context.refresh();

            PrototypeConsumer first = context.getBean(PrototypeConsumer.class);
            PrototypeConsumer second = context.getBean(PrototypeConsumer.class);

            assertThat(first.adapter()).isExactlyInstanceOf(RequestScopedStandIn.class);
            assertThat(first.adapter()).isSameAs(first.adapter());
            assertThat(first.adapter()).isNotSameAs(second.adapter());
        }
    }

    @Test
    void doesNothingWhenNoScopedTargetIsRegistered() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            BeanFactoryPostProcessor processor = WorkerDomainConfig.coinGeckoQuoteAdapterPrototypeScoped();

            assertThatCode(() -> processor.postProcessBeanFactory(context.getBeanFactory()))
                    .doesNotThrowAnyException();
        }
    }
}
