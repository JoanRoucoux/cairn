package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.IntradayValuation;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.out.SaveValuationPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValuationServiceTest {

    private static final Portfolio PORTFOLIO = new Portfolio(
            Money.eur(new BigDecimal("25950")),
            Money.eur(new BigDecimal("125.50")),
            Optional.empty(),
            List.of(),
            List.of(),
            List.<ValuedHolding>of(),
            0,
            0,
            0);

    @Test
    void writesAPointTruncatedToTheMinuteWithThePortfolioTotal() {
        List<IntradayValuation> saved = new ArrayList<>();
        SaveValuationPort savePort = new SaveValuationPort() {
            @Override
            public void upsert(IntradayValuation valuation) {
                saved.add(valuation);
            }

            @Override
            public void deleteBefore(Instant cutoff) {}
        };
        ValuationService service = new ValuationService(() -> PORTFOLIO, savePort);

        IntradayValuation recorded = service.record(Instant.parse("2026-09-24T13:47:31Z"));

        assertThat(recorded.at()).isEqualTo(Instant.parse("2026-09-24T13:47:00Z"));
        assertThat(recorded.totalEur()).isEqualByComparingTo("25950");
        assertThat(saved).containsExactly(recorded);
    }

    @Test
    void purgesPointsOlderThanThirtyDays() {
        List<Instant> cutoffs = new ArrayList<>();
        SaveValuationPort savePort = new SaveValuationPort() {
            @Override
            public void upsert(IntradayValuation valuation) {}

            @Override
            public void deleteBefore(Instant cutoff) {
                cutoffs.add(cutoff);
            }
        };
        ValuationService service = new ValuationService(() -> PORTFOLIO, savePort);

        service.record(Instant.parse("2026-09-24T13:47:31Z"));

        assertThat(cutoffs)
                .containsExactly(Instant.parse("2026-09-24T13:47:00Z").minus(Duration.ofDays(30)));
    }

    @Test
    void writesThenPurgesInOrder() {
        List<String> order = new ArrayList<>();
        SaveValuationPort savePort = new SaveValuationPort() {
            @Override
            public void upsert(IntradayValuation valuation) {
                order.add("upsert");
            }

            @Override
            public void deleteBefore(Instant cutoff) {
                order.add("deleteBefore");
            }
        };
        ValuationService service = new ValuationService(() -> PORTFOLIO, savePort);

        service.record(Instant.parse("2026-09-24T13:47:31Z"));

        assertThat(order).containsExactly("upsert", "deleteBefore");
    }
}
