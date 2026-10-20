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

        assertThat(latestTwo(instrumentId).get(0).price()).isEqualByComparingTo("690.00");
        assertThat(quotes.findBetween(instrumentId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .hasSize(1);
    }

    @Test
    void replacesEveryColumnOfAnExistingQuote() {
        UUID instrumentId = givenAnInstrument();
        LocalDate day = LocalDate.of(2026, 8, 21);
        quotes.upsert(new Quote(
                instrumentId,
                day,
                new BigDecimal("1.00"),
                "EUR",
                PriceSource.YAHOO,
                Instant.parse("2026-08-21T10:00:00Z")));

        quotes.upsert(new Quote(
                instrumentId,
                day,
                new BigDecimal("2.50"),
                "USD",
                PriceSource.MANUAL,
                Instant.parse("2026-08-22T11:30:00Z")));

        assertThat(latestTwo(instrumentId)).singleElement().satisfies(stored -> {
            assertThat(stored.price()).isEqualByComparingTo("2.50");
            assertThat(stored.currency()).isEqualTo("USD");
            assertThat(stored.source()).isEqualTo(PriceSource.MANUAL);
            assertThat(stored.fetchedAt()).isEqualTo(Instant.parse("2026-08-22T11:30:00Z"));
        });
    }

    @Test
    void readsTheMostRecentQuote() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsertAll(List.of(
                quote(instrumentId, LocalDate.of(2026, 8, 20), new BigDecimal("690.81")),
                quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("686.31"))));

        assertThat(latestTwo(instrumentId).get(0).asOf()).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void readsTheQuoteJustBeforeAGivenDate() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsertAll(List.of(
                quote(instrumentId, LocalDate.of(2026, 8, 20), new BigDecimal("690.81")),
                quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("686.31"))));

        assertThat(latestTwo(instrumentId).get(1).price()).isEqualByComparingTo("690.81");
    }

    @Test
    void keepsTwelveDecimalsOfPrice() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsert(quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("0.000012345678")));

        assertThat(latestTwo(instrumentId).get(0).price()).isEqualByComparingTo("0.000012345678");
    }

    @Test
    void keepsAPriceAboveTenTrillion() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsert(quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("12345678901234.123456789012")));

        assertThat(latestTwo(instrumentId).get(0).price()).isEqualByComparingTo("12345678901234.123456789012");
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
    void readsForEachInstrumentItsTwoLatestQuotesNewestFirst() {
        UUID fund = givenAnInstrument();
        UUID etf = givenAnInstrument();
        UUID young = givenAnInstrument();
        UUID untouched = givenAnInstrument();
        quotes.upsertAll(List.of(
                quote(fund, LocalDate.of(2026, 9, 11), new BigDecimal("60.10")),
                quote(fund, LocalDate.of(2026, 9, 18), new BigDecimal("60.39")),
                quote(fund, LocalDate.of(2026, 9, 17), new BigDecimal("60.20")),
                quote(etf, LocalDate.of(2026, 9, 23), new BigDecimal("705.00")),
                quote(young, LocalDate.of(2026, 9, 23), new BigDecimal("10.00"))));

        Map<UUID, List<Quote>> latestTwo = quotes.findLatestTwo(Set.of(fund, etf, young, untouched));

        assertThat(latestTwo).containsOnlyKeys(fund, etf, young);
        assertThat(latestTwo.get(fund))
                .extracting(Quote::asOf)
                .containsExactly(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 17));
        assertThat(latestTwo.get(etf)).hasSize(1);
        assertThat(latestTwo.get(young)).hasSize(1);
    }

    @Test
    void readsNoLatestTwoForNoInstrument() {
        assertThat(quotes.findLatestTwo(Set.of())).isEmpty();
    }

    @Test
    void upsertsABatchWithoutDuplicatingExistingDays() {
        UUID instrumentId = givenAnInstrument();
        quotes.upsert(quote(instrumentId, LocalDate.of(2026, 8, 20), new BigDecimal("1.00")));

        quotes.upsertAll(List.of(
                quote(instrumentId, LocalDate.of(2026, 8, 20), new BigDecimal("2.00")),
                quote(instrumentId, LocalDate.of(2026, 8, 21), new BigDecimal("3.00"))));

        assertThat(quotes.findBetween(instrumentId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .extracting(Quote::price)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2.00"), new BigDecimal("3.00"));
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

    private List<Quote> latestTwo(UUID instrumentId) {
        return quotes.findLatestTwo(Set.of(instrumentId)).get(instrumentId);
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
