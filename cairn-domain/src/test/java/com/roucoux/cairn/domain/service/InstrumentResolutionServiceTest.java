package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class InstrumentResolutionServiceTest {

    private static final InstrumentCandidate ETF_CANDIDATE = new InstrumentCandidate(
            "Amundi MSCI World", PriceSource.YAHOO, "ETF.PA", AssetClass.ETF, "Paris", BigDecimal.TEN);
    private static final InstrumentCandidate SG_CANDIDATE = new InstrumentCandidate(
            "Societe Generale", PriceSource.SG_SIRIUS, "QS0000000010", AssetClass.EQUITY, "Paris", BigDecimal.ONE);

    @Test
    void returnsEveryCandidateFoundAcrossSources() {
        InstrumentResolutionService service =
                new InstrumentResolutionService(List.of(resolver(ETF_CANDIDATE), resolver()), List.of());

        assertThat(service.resolve("LU0000000010")).containsExactly(ETF_CANDIDATE);
    }

    @Test
    void keepsCandidatesFromEverySourceThatAnswers() {
        InstrumentResolutionService service =
                new InstrumentResolutionService(List.of(resolver(ETF_CANDIDATE), resolver(SG_CANDIDATE)), List.of());

        assertThat(service.resolve("QS0000000010")).containsExactly(ETF_CANDIDATE, SG_CANDIDATE);
    }

    @Test
    void ignoresASourceThatFailsRatherThanLosingTheOthers() {
        InstrumentResolutionService service =
                new InstrumentResolutionService(List.of(failingResolver(), resolver(SG_CANDIDATE)), List.of());

        assertThat(service.resolve("QS0000000010")).containsExactly(SG_CANDIDATE);
    }

    @Test
    void raisesWhenNoSourceKnowsTheInstrument() {
        InstrumentResolutionService service = new InstrumentResolutionService(List.of(resolver()), List.of());

        assertThatThrownBy(() -> service.resolve("XX0000000000")).isInstanceOf(UnknownInstrumentException.class);
    }

    @Test
    void probesALivePriceForEachCandidate() {
        ResolveInstrumentPort yahoo = resolver(new InstrumentCandidate(
                "Amundi MSCI World", PriceSource.YAHOO, "CW8.PA", AssetClass.ETF, "Paris", null));
        FetchQuotePort quotes = fetcher(PriceSource.YAHOO, instrument -> new BigDecimal("559.30"));

        List<InstrumentCandidate> candidates =
                new InstrumentResolutionService(List.of(yahoo), List.of(quotes)).resolve("amundi world");

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.exchange()).isEqualTo("Paris");
            assertThat(candidate.probePrice()).isEqualByComparingTo("559.30");
        });
    }

    @Test
    void aFailedProbeKeepsTheCandidateWithoutAPrice() {
        ResolveInstrumentPort yahoo = resolver(
                new InstrumentCandidate("Accor", PriceSource.YAHOO, "AC.PA", AssetClass.EQUITY, "Paris", null));
        FetchQuotePort failing = fetcher(PriceSource.YAHOO, instrument -> {
            throw new MarketDataUnavailableException("yahoo down");
        });

        List<InstrumentCandidate> candidates =
                new InstrumentResolutionService(List.of(yahoo), List.of(failing)).resolve("accor");

        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.probePrice()).isNull());
    }

    @Test
    void aProbeThatTakesTooLongKeepsTheCandidateWithoutAPrice() {
        ResolveInstrumentPort yahoo = resolver(
                new InstrumentCandidate("Accor", PriceSource.YAHOO, "AC.PA", AssetClass.EQUITY, "Paris", null));
        FetchQuotePort slow = fetcher(PriceSource.YAHOO, instrument -> {
            try {
                Thread.sleep(Duration.ofSeconds(2).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new BigDecimal("30.00");
        });

        List<InstrumentCandidate> candidates =
                new InstrumentResolutionService(List.of(yahoo), List.of(slow), Duration.ofMillis(50)).resolve("accor");

        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.probePrice()).isNull());
    }

    private static FetchQuotePort fetcher(PriceSource source, Function<Instrument, BigDecimal> priceFor) {
        return new FetchQuotePort() {
            @Override
            public boolean supports(PriceSource candidate) {
                return candidate == source;
            }

            @Override
            public Quote fetch(Instrument instrument) {
                return new Quote(
                        instrument.id(), LocalDate.now(), priceFor.apply(instrument), "EUR", source, Instant.now());
            }

            @Override
            public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
                return List.of();
            }
        };
    }

    private static ResolveInstrumentPort resolver(InstrumentCandidate... candidates) {
        return new StubResolveInstrumentPort(List.of(candidates));
    }

    private static ResolveInstrumentPort failingResolver() {
        return new FailingResolveInstrumentPort();
    }

    private static final class StubResolveInstrumentPort implements ResolveInstrumentPort {
        private final List<InstrumentCandidate> candidates;

        private StubResolveInstrumentPort(List<InstrumentCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public boolean supports(PriceSource source) {
            return true;
        }

        @Override
        public List<InstrumentCandidate> resolve(String query) {
            return candidates;
        }
    }

    private static final class FailingResolveInstrumentPort implements ResolveInstrumentPort {
        @Override
        public boolean supports(PriceSource source) {
            return true;
        }

        @Override
        public List<InstrumentCandidate> resolve(String query) {
            throw new MarketDataUnavailableException("simulated failure for " + query);
        }
    }
}
