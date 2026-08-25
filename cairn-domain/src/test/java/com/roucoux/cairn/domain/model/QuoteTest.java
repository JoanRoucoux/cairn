package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteTest {

    @Test
    void exposesItsFields() {
        UUID instrumentId = UUID.randomUUID();
        LocalDate asOf = LocalDate.of(2026, 8, 25);
        Instant fetchedAt = Instant.parse("2026-08-25T10:00:00Z");

        Quote quote = new Quote(instrumentId, asOf, new BigDecimal("123.45"), "EUR", PriceSource.YAHOO, fetchedAt);

        assertThat(quote.instrumentId()).isEqualTo(instrumentId);
        assertThat(quote.asOf()).isEqualTo(asOf);
        assertThat(quote.price()).isEqualByComparingTo("123.45");
        assertThat(quote.currency()).isEqualTo("EUR");
        assertThat(quote.source()).isEqualTo(PriceSource.YAHOO);
        assertThat(quote.fetchedAt()).isEqualTo(fetchedAt);
    }
}
