package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.hikari.connection-timeout=250"})
@Import({QuotePersistenceAdapter.class, InstrumentPersistenceAdapter.class})
class QuotePersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    QuotePersistenceAdapter quotes;

    @Autowired
    InstrumentPersistenceAdapter instruments;

    @Test
    void replacesTheQuoteOfADayInsteadOfDuplicatingIt() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsert(quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("686.31")));

        quotes.upsert(quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("690.00")));

        assertThat(quotes.findLatest(instrumentId).orElseThrow().price()).isEqualByComparingTo("690.00");
        assertThat(quotes.findBetween(instrumentId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .hasSize(1);
    }

    @Test
    void readsTheMostRecentQuote() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsertAll(List.of(
                quote(instrumentId, LocalDate.of(2026, 8, 20), new BigDecimal("690.81")),
                quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("686.31"))));

        assertThat(quotes.findLatest(instrumentId).orElseThrow().asOf()).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void readsTheQuoteJustBeforeAGivenDate() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsertAll(List.of(
                quote(instrumentId, LocalDate.of(2026, 8, 20), new BigDecimal("690.81")),
                quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("686.31"))));

        assertThat(quotes.findPrevious(instrumentId, LocalDate.of(2026, 8, 21))
                        .orElseThrow()
                        .price())
                .isEqualByComparingTo("690.81");
    }

    @Test
    void keepsSixDecimalsOfPrice() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsert(quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("33.306900")));

        assertThat(quotes.findLatest(instrumentId).orElseThrow().price()).isEqualByComparingTo("33.3069");
    }

    @Test
    void readsForEachInstrumentItsLatestQuoteOnOrBeforeADay() {
        UUID fund = givenAnInstrument();
        UUID etf = givenAnInstrument();
        UUID young = givenAnInstrument();
        quotes.upsertAll(List.of(
                quote(fund, LocalDate.of(2026, 9, 11), new BigDecimal("60.10")),
                quote(fund, LocalDate.of(2026, 9, 18), new BigDecimal("60.39")),
                quote(etf, LocalDate.of(2026, 9, 22), new BigDecimal("703.73")),
                quote(etf, LocalDate.of(2026, 9, 23), new BigDecimal("705.00")),
                quote(young, LocalDate.of(2026, 9, 23), new BigDecimal("10.00"))));

        Map<UUID, Quote> latest = quotes.findLatestOnOrBefore(Set.of(fund, etf, young), LocalDate.of(2026, 9, 22));

        assertThat(latest).containsOnlyKeys(fund, etf);
        assertThat(latest.get(fund).asOf()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(latest.get(etf).price()).isEqualByComparingTo("703.73");
    }

    @Test
    void readsNothingForNoInstrument() {
        assertThat(quotes.findLatestOnOrBefore(Set.of(), LocalDate.of(2026, 9, 22)))
                .isEmpty();
    }

    @Test
    void readsForEachInstrumentTheDateOfItsEarliestQuote() {
        UUID fund = givenAnInstrument();
        UUID etf = givenAnInstrument();
        quotes.upsertAll(List.of(
                quote(fund, LocalDate.of(2020, 1, 15), new BigDecimal("40.00")),
                quote(fund, LocalDate.of(2024, 1, 2), new BigDecimal("48.00")),
                quote(etf, LocalDate.of(2024, 1, 2), new BigDecimal("100.00"))));

        Map<UUID, LocalDate> firstQuoteDates = quotes.findFirstQuoteDates(Set.of(fund, etf));

        assertThat(firstQuoteDates.get(fund)).isEqualTo(LocalDate.of(2020, 1, 15));
        assertThat(firstQuoteDates.get(etf)).isEqualTo(LocalDate.of(2024, 1, 2));
    }

    @Test
    void readsNoFirstQuoteDateForNoInstrument() {
        assertThat(quotes.findFirstQuoteDates(Set.of())).isEmpty();
    }

    private UUID givenAnInstrument() {
        return instruments
                .save(new Instrument(
                        UUID.randomUUID(),
                        "MSCI World",
                        null,
                        "EUR",
                        AssetClass.ETF,
                        PriceSource.YAHOO,
                        "T" + UUID.randomUUID().toString().substring(0, 8),
                        null))
                .id();
    }

    private Quote quote(UUID instrumentId, LocalDate asOf, BigDecimal price) {
        return new Quote(instrumentId, asOf, price, "EUR", PriceSource.YAHOO, Instant.now());
    }
}
