package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roucoux.cairn.adapter.client.config.TelegramClientConfig;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.CashOnlyAccount;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.Recipient;
import com.roucoux.cairn.domain.model.Summary;
import com.roucoux.cairn.domain.model.SummaryPeriod;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class TelegramNotificationAdapterTest {

    private static final String TOKEN = "123456:super-secret-token";
    private static final String ENCODED_TOKEN = "123456%3Asuper-secret-token";
    private static final long CHAT_ID = 42L;

    private WireMockServer wireMock;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private TelegramNotificationAdapter adapter() {
        TelegramClientProperties properties =
                new TelegramClientProperties(wireMock.baseUrl(), TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(5));
        RestClient client = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                        .build(HttpClientSettings.defaults()))
                .build();
        return new TelegramNotificationAdapter(
                new TelegramBotApi(client, properties, new ObjectMapper(), duration -> {}),
                new TelegramClientConfig().telegramMessages());
    }

    private static Summary exampleSummary() {
        Performance performance = new Performance(
                Optional.of(PerformanceRange.D1),
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 9, 23),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("372899")),
                Money.eur(new BigDecimal("297")),
                Optional.of(new BigDecimal("0.0010")),
                List.of(
                        envelope(AccountType.PEA, "185430", "1204", "0.0061"),
                        envelope(AccountType.PEE, "112380", "160", "0.0014"),
                        envelope(AccountType.CRYPTO, "55079", "-1067", "-0.0192"),
                        envelope(AccountType.SAVINGS, "20010", "0", "0")));
        return new Summary(
                SummaryPeriod.DAY,
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 23),
                performance,
                List.of(
                        new CashOnlyAccount("Livret A", AccountType.SAVINGS, Money.eur(new BigDecimal("20000"))),
                        new CashOnlyAccount("LDDS", AccountType.SAVINGS, Money.eur(new BigDecimal("10")))));
    }

    private static EnvelopePerformance envelope(AccountType type, String value, String change, String ratio) {
        return new EnvelopePerformance(
                type,
                Money.eur(new BigDecimal(value)),
                new BigDecimal("0.1"),
                Money.eur(new BigDecimal(change)),
                Optional.ofNullable(ratio).map(BigDecimal::new));
    }

    private static final String EXPECTED_TEXT = """
            📊 <b>Cairn · Mercredi 23 septembre</b>

            💰 Patrimoine  <b>372 899 €</b>
            📈 Jour  <b>+297 €</b>  (+0,10 %)

            <pre>🟢 PEA      185 430 €  +1 204 €  +0,61%
            🟢 PEE      112 380 €    +160 €  +0,14%
            🔴 Crypto    55 079 €  -1 067 €  -1,92%
            ───────────────────────────────────────
            🏦 Livret A  20 000 €
            🏦 LDDS          10 €</pre>""";

    @Test
    void sendsTheFormattedMessageAsHtmlToTheConfiguredChat() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter().send(new Recipient(CHAT_ID, Language.FR), exampleSummary());

        wireMock.verify(postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage"))
                .withRequestBody(equalToJson(
                        "{\"chat_id\":" + CHAT_ID + ",\"parse_mode\":\"HTML\",\"text\":" + toJsonString(EXPECTED_TEXT)
                                + "}",
                        true,
                        false)));
    }

    @Test
    void postsToTheChatOfTheRecipient() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter().send(new Recipient(987654321L, Language.FR), exampleSummary());

        wireMock.verify(postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage"))
                .withRequestBody(equalToJson("{\"chat_id\":987654321}", true, true)));
    }

    @Test
    void aLosingDayShowsAFallingChartAGreyDotForAFlatEnvelopeAndNoRatioWhenThereIsNone() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));
        Performance performance = new Performance(
                Optional.of(PerformanceRange.D1),
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("100000")),
                Money.eur(new BigDecimal("-1500")),
                Optional.of(new BigDecimal("-0.0148")),
                List.of(
                        envelope(AccountType.LIFE_INSURANCE, "60000", "0.30", "0.000005"),
                        envelope(AccountType.SAVINGS, "40000", "-1500.30", null)));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.FR),
                        new Summary(
                                SummaryPeriod.DAY,
                                LocalDate.of(2026, 8, 14),
                                LocalDate.of(2026, 8, 14),
                                performance,
                                List.of()));

        assertThat(sentText()).isEqualTo("""
                📊 <b>Cairn · Vendredi 14 août</b>

                💰 Patrimoine  <b>100 000 €</b>
                📉 Jour  <b>-1 500 €</b>  (-1,48 %)

                <pre>⚪ Assu. vie 60 000 €       0 €  0,00%
                🔴 Livrets   40 000 €  -1 500 €</pre>""");
    }

    @Test
    void escapesAnAccountNameSoTelegramStillParsesTheHtml() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));
        Performance performance = new Performance(
                Optional.of(PerformanceRange.D1),
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("500")),
                Money.eur(BigDecimal.ZERO),
                Optional.of(BigDecimal.ZERO),
                List.of(envelope(AccountType.SAVINGS, "500", "0", "0")));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.FR),
                        new Summary(
                                SummaryPeriod.DAY,
                                LocalDate.of(2026, 8, 14),
                                LocalDate.of(2026, 8, 14),
                                performance,
                                List.of(new CashOnlyAccount(
                                        "Livret <A&B>", AccountType.SAVINGS, Money.eur(new BigDecimal("500"))))));

        assertThat(sentText()).endsWith("<pre>🏦 Livret &lt;A&amp;B&gt; 500 €</pre>");
    }

    private static Summary recap(SummaryPeriod period, LocalDate first, LocalDate last, LocalDate performanceFrom) {
        Performance performance = new Performance(
                Optional.empty(),
                performanceFrom,
                last.plusDays(1),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("50000")),
                Money.eur(new BigDecimal("1234")),
                Optional.of(new BigDecimal("0.0210")),
                List.of(envelope(AccountType.PEA, "50000", "1234", "0.0210")));
        return new Summary(period, first, last, performance, List.of());
    }

    private static String recapText(String title, String label) {
        return """
                📊 <b>Cairn · %s</b>

                💰 Patrimoine  <b>50 000 €</b>
                📈 %s  <b>+1 234 €</b>  (+2,10 %%)

                <pre>🟢 PEA 50 000 €  +1 234 €  +2,10%%</pre>""".formatted(title, label);
    }

    @Test
    void aWeeklyRecapNamesTheDaysOfTheWeekInOneMonth() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.FR),
                        recap(
                                SummaryPeriod.WEEK,
                                LocalDate.of(2026, 10, 12),
                                LocalDate.of(2026, 10, 16),
                                LocalDate.of(2026, 10, 11)));

        assertThat(sentText()).isEqualTo(recapText("Semaine du 12 au 16 octobre", "Semaine"));
    }

    @Test
    void aWeeklyRecapSpanningTwoMonthsNamesBothMonths() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.FR),
                        recap(
                                SummaryPeriod.WEEK,
                                LocalDate.of(2026, 9, 28),
                                LocalDate.of(2026, 10, 2),
                                LocalDate.of(2026, 9, 27)));

        assertThat(sentText()).isEqualTo(recapText("Semaine du 28 septembre au 2 octobre", "Semaine"));
    }

    @Test
    void aMonthlyRecapIsTitledWithTheMonthAndYear() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.FR),
                        recap(
                                SummaryPeriod.MONTH,
                                LocalDate.of(2026, 10, 1),
                                LocalDate.of(2026, 10, 31),
                                LocalDate.of(2026, 9, 30)));

        assertThat(sentText()).isEqualTo(recapText("Octobre 2026", "Mois"));
    }

    @Test
    void aYearlyRecapIsTitledBilanWithTheYear() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.FR),
                        recap(
                                SummaryPeriod.YEAR,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31),
                                LocalDate.of(2025, 12, 31)));

        assertThat(sentText()).isEqualTo(recapText("Bilan 2026", "Année"));
    }

    @Test
    void aRecapWhoseHistoryStartsAfterThePeriodSaysSinceWhen() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.FR),
                        recap(
                                SummaryPeriod.YEAR,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31),
                                LocalDate.of(2026, 9, 24)));

        assertThat(sentText()).isEqualTo(recapText("Bilan 2026", "Année (depuis le 24 septembre)"));
    }

    private static final String EXPECTED_ENGLISH_TEXT = """
            📊 <b>Cairn · Wednesday 23 September</b>

            💰 Net worth  <b>€372,899</b>
            📈 Day  <b>+€297</b>  (+0.10%)

            <pre>🟢 PEA      €185,430  +€1,204  +0.61%
            🟢 PEE      €112,380    +€160  +0.14%
            🔴 Crypto    €55,079  -€1,067  -1.92%
            ─────────────────────────────────────
            🏦 Livret A  €20,000
            🏦 LDDS          €10</pre>""";

    @Test
    void anEnglishDailyRecapUsesEnglishWordsAndEuroPrefix() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter().send(new Recipient(CHAT_ID, Language.EN), exampleSummary());

        assertThat(sentText()).isEqualTo(EXPECTED_ENGLISH_TEXT);
    }

    private static String englishRecapText(String title, String label) {
        return """
                📊 <b>Cairn · %s</b>

                💰 Net worth  <b>€50,000</b>
                📈 %s  <b>+€1,234</b>  (+2.10%%)

                <pre>🟢 PEA €50,000  +€1,234  +2.10%%</pre>""".formatted(title, label);
    }

    @Test
    void anEnglishWeeklyRecapSpanningTwoMonthsNamesBothMonths() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.EN),
                        recap(
                                SummaryPeriod.WEEK,
                                LocalDate.of(2026, 9, 28),
                                LocalDate.of(2026, 10, 2),
                                LocalDate.of(2026, 9, 27)));

        assertThat(sentText()).isEqualTo(englishRecapText("Week of 28 September to 2 October", "Week"));
    }

    @Test
    void anEnglishWeeklyRecapInOneMonthNamesTheDaysOnly() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.EN),
                        recap(
                                SummaryPeriod.WEEK,
                                LocalDate.of(2026, 10, 12),
                                LocalDate.of(2026, 10, 16),
                                LocalDate.of(2026, 10, 11)));

        assertThat(sentText()).isEqualTo(englishRecapText("Week of 12 to 16 October", "Week"));
    }

    @Test
    void anEnglishMonthlyRecapIsTitledWithTheMonthAndYear() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.EN),
                        recap(
                                SummaryPeriod.MONTH,
                                LocalDate.of(2026, 10, 1),
                                LocalDate.of(2026, 10, 31),
                                LocalDate.of(2026, 9, 30)));

        assertThat(sentText()).isEqualTo(englishRecapText("October 2026", "Month"));
    }

    @Test
    void anEnglishYearlyRecapIsTitledReview() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.EN),
                        recap(
                                SummaryPeriod.YEAR,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31),
                                LocalDate.of(2025, 12, 31)));

        assertThat(sentText()).isEqualTo(englishRecapText("2026 review", "Year"));
    }

    @Test
    void anEnglishRecapWhoseHistoryStartsAfterThePeriodSaysSince() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.EN),
                        recap(
                                SummaryPeriod.YEAR,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31),
                                LocalDate.of(2026, 9, 24)));

        assertThat(sentText()).isEqualTo(englishRecapText("2026 review", "Year (since 24 September)"));
    }

    @Test
    void anEnglishTableAbbreviatesLifeInsurance() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));
        Performance performance = new Performance(
                Optional.of(PerformanceRange.D1),
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("100000")),
                Money.eur(new BigDecimal("-1500")),
                Optional.of(new BigDecimal("-0.0148")),
                List.of(
                        envelope(AccountType.LIFE_INSURANCE, "60000", "0.30", "0.000005"),
                        envelope(AccountType.SAVINGS, "40000", "-1500.30", null)));

        adapter()
                .send(
                        new Recipient(CHAT_ID, Language.EN),
                        new Summary(
                                SummaryPeriod.DAY,
                                LocalDate.of(2026, 8, 14),
                                LocalDate.of(2026, 8, 14),
                                performance,
                                List.of()));

        assertThat(sentText()).isEqualTo("""
                📊 <b>Cairn · Friday 14 August</b>

                💰 Net worth  <b>€100,000</b>
                📉 Day  <b>-€1,500</b>  (-1.48%)

                <pre>⚪ Life ins. €60,000       €0  0.00%
                🔴 Savings   €40,000  -€1,500</pre>""");
    }

    private String sentText() {
        String body = wireMock.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        return new ObjectMapper().readTree(body).path("text").asString();
    }

    private static String toJsonString(String text) {
        try {
            return new ObjectMapper().writeValueAsString(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
