package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import com.roucoux.cairn.domain.service.InstrumentResolutionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class InstrumentResolutionRequestScopeTest {

    private AnnotationConfigWebApplicationContext context;

    @BeforeEach
    void startRequest() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Fetchers.class);
        context.refresh();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void endRequest() {
        RequestContextHolder.resetRequestAttributes();
        context.close();
    }

    @Test
    void aRequestScopedFetcherAheadOfTheMatchingOneDoesNotBreakTheProbe() {
        List<FetchQuotePort> fetchers = List.of(
                context.getBean("requestScopedFetcher", FetchQuotePort.class),
                context.getBean("plainFetcher", FetchQuotePort.class));
        InstrumentCandidate candidate = new InstrumentCandidate(
                "Woodgrove", PriceSource.YAHOO, "WGV.PA", AssetClass.EQUITY, "Paris", null, "WGV.PA", null, null);
        ResolveInstrumentPort resolver = new ResolveInstrumentPort() {
            @Override
            public boolean supports(PriceSource source) {
                return source == PriceSource.YAHOO;
            }

            @Override
            public List<InstrumentCandidate> resolve(String query) {
                return List.of(candidate);
            }
        };
        InstrumentResolutionService service =
                new InstrumentResolutionService(List.of(resolver), fetchers, new NoInstruments());

        assertThat(service.search(PriceSource.YAHOO, "woodgrove"))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.probePrice()).isEqualByComparingTo("12.50");
                    assertThat(found.currency()).isEqualTo("EUR");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Fetchers {

        @Bean
        @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
        RequestScopedFetcher requestScopedFetcher() {
            return new RequestScopedFetcher();
        }

        @Bean
        PlainFetcher plainFetcher() {
            return new PlainFetcher();
        }
    }

    static class RequestScopedFetcher implements FetchQuotePort {
        @Override
        public boolean supports(PriceSource source) {
            return source == PriceSource.COINGECKO;
        }

        @Override
        public Quote fetch(Instrument instrument) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
            return List.of();
        }
    }

    static class PlainFetcher implements FetchQuotePort {
        @Override
        public boolean supports(PriceSource source) {
            return source == PriceSource.YAHOO;
        }

        @Override
        public Quote fetch(Instrument instrument) {
            return new Quote(
                    instrument.id(),
                    LocalDate.of(2026, 10, 2),
                    new BigDecimal("12.50"),
                    "EUR",
                    PriceSource.YAHOO,
                    Instant.now());
        }

        @Override
        public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
            return List.of();
        }
    }

    static class NoInstruments implements LoadInstrumentsPort {
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
    }
}
