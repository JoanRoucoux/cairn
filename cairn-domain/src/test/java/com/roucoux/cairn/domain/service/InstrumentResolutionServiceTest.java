package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentResolutionServiceTest {

    private static final InstrumentCandidate CW8_CANDIDATE =
            new InstrumentCandidate("Amundi MSCI World", PriceSource.YAHOO, "CW8.PA", AssetClass.ETF, BigDecimal.TEN);
    private static final InstrumentCandidate SG_CANDIDATE = new InstrumentCandidate(
            "Societe Generale", PriceSource.SG_SIRIUS, "QS0002904819", AssetClass.EQUITY, BigDecimal.ONE);

    @Test
    void returnsEveryCandidateFoundAcrossSources() {
        InstrumentResolutionService service =
                new InstrumentResolutionService(List.of(resolver(CW8_CANDIDATE), resolver()));

        assertThat(service.resolve("LU1681043599")).containsExactly(CW8_CANDIDATE);
    }

    @Test
    void keepsCandidatesFromEverySourceThatAnswers() {
        InstrumentResolutionService service =
                new InstrumentResolutionService(List.of(resolver(CW8_CANDIDATE), resolver(SG_CANDIDATE)));

        assertThat(service.resolve("QS0002904819")).containsExactly(CW8_CANDIDATE, SG_CANDIDATE);
    }

    @Test
    void ignoresASourceThatFailsRatherThanLosingTheOthers() {
        InstrumentResolutionService service =
                new InstrumentResolutionService(List.of(failingResolver(), resolver(SG_CANDIDATE)));

        assertThat(service.resolve("QS0002904819")).containsExactly(SG_CANDIDATE);
    }

    @Test
    void raisesWhenNoSourceKnowsTheInstrument() {
        InstrumentResolutionService service = new InstrumentResolutionService(List.of(resolver()));

        assertThatThrownBy(() -> service.resolve("XX0000000000")).isInstanceOf(UnknownInstrumentException.class);
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
