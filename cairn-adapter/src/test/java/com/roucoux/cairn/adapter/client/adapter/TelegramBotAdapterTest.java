package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.roucoux.cairn.adapter.client.config.TelegramClientConfig;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.StartCommand;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class TelegramBotAdapterTest {

    private static final String TOKEN = "123456:super-secret-token";

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

    private TelegramBotAdapter adapter(String token) {
        TelegramClientProperties properties =
                new TelegramClientProperties(wireMock.baseUrl(), token, Duration.ofSeconds(2), Duration.ofSeconds(5));
        RestClient client = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                        .build(HttpClientSettings.defaults()))
                .build();
        return new TelegramBotAdapter(
                new TelegramBotApi(client, properties, new ObjectMapper(), duration -> {}),
                new TelegramClientConfig().telegramMessages());
    }

    private void stubUpdates(String result) {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/getUpdates"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":" + result + "}")));
    }

    private static String update(long id, long chatId, String type, String chatFields, String text) {
        return "{\"update_id\":" + id + ",\"message\":{\"message_id\":1,\"chat\":{\"id\":" + chatId + ",\"type\":\""
                + type + "\"" + chatFields + "},\"text\":\"" + text + "\"}}";
    }

    @Test
    void isConfiguredOnlyWithAToken() {
        assertThat(adapter(TOKEN).configured()).isTrue();
        assertThat(adapter("").configured()).isFalse();
    }

    @Test
    void memoizesTheBotUsername() {
        wireMock.stubFor(get(urlPathTemplate("/bot{token}/getMe"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":{\"username\":\"cairn_bot\"}}")));
        TelegramBotAdapter adapter = adapter(TOKEN);

        assertThat(adapter.botUsername()).isEqualTo("cairn_bot");
        assertThat(adapter.botUsername()).isEqualTo("cairn_bot");

        wireMock.verify(1, getRequestedFor(urlPathTemplate("/bot{token}/getMe")));
    }

    @Test
    void aBlankBotUsernameIsADeliveryFailureAndIsNotMemoized() {
        wireMock.stubFor(get(urlPathTemplate("/bot{token}/getMe"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":{\"username\":\"\"}}")));
        TelegramBotAdapter adapter = adapter(TOKEN);

        assertThatThrownBy(adapter::botUsername).isInstanceOf(NotificationDeliveryException.class);
        assertThatThrownBy(adapter::botUsername).isInstanceOf(NotificationDeliveryException.class);

        wireMock.verify(2, getRequestedFor(urlPathTemplate("/bot{token}/getMe")));
    }

    @Test
    void keepsOnlyPrivateStartCommands() {
        stubUpdates("[" + update(10, 111, "private", ",\"username\":\"alex\"", "/start abc") + ","
                + update(11, -222, "group", ",\"title\":\"Team\"", "/start def") + ","
                + update(12, 333, "private", ",\"first_name\":\"Sam\"", "hello there") + ","
                + update(13, 444, "private", ",\"first_name\":\"Robin\"", "/start") + ","
                + update(14, 555, "private", ",\"first_name\":\"Robin\"", "/start xyz") + "]");

        List<StartCommand> commands = adapter(TOKEN).drainStartCommands();

        assertThat(commands)
                .containsExactly(new StartCommand(111L, "@alex", "abc"), new StartCommand(555L, "Robin", "xyz"));
    }

    @Test
    void acknowledgesTheBatchWithTheNextOffset() {
        stubUpdates("[" + update(10, 111, "private", ",\"username\":\"alex\"", "/start abc") + ","
                + update(15, -222, "group", "", "hi") + "]");

        adapter(TOKEN).drainStartCommands();

        wireMock.verify(2, postRequestedFor(urlPathTemplate("/bot{token}/getUpdates")));
        wireMock.verify(postRequestedFor(urlPathTemplate("/bot{token}/getUpdates"))
                .withRequestBody(equalToJson(
                        "{\"offset\":16,\"limit\":1,\"timeout\":0,\"allowed_updates\":[\"message\"]}", true, false)));
    }

    @Test
    void makesNoSecondCallOnAnEmptyBatch() {
        stubUpdates("[]");

        assertThat(adapter(TOKEN).drainStartCommands()).isEmpty();

        wireMock.verify(1, postRequestedFor(urlPathTemplate("/bot{token}/getUpdates")));
    }

    @Test
    void concurrentDrainsNeverOverlapTheirGetUpdatesCalls() throws Exception {
        long delayMillis = 300;
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/getUpdates"))
                .willReturn(aResponse()
                        .withFixedDelay((int) delayMillis)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":[]}")));
        TelegramBotAdapter adapter = adapter(TOKEN);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<StartCommand>>> drains = List.of(
                    executor.submit(() -> {
                        start.await();
                        return adapter.drainStartCommands();
                    }),
                    executor.submit(() -> {
                        start.await();
                        return adapter.drainStartCommands();
                    }));
            start.countDown();
            for (Future<List<StartCommand>> drain : drains) {
                assertThat(drain.get()).isEmpty();
            }
        } finally {
            executor.shutdownNow();
        }

        List<Instant> receivedAt = wireMock.getAllServeEvents().stream()
                .map(ServeEvent::getRequest)
                .map(request -> request.getLoggedDate().toInstant())
                .sorted()
                .toList();
        assertThat(receivedAt).hasSize(2);
        assertThat(Duration.between(receivedAt.get(0), receivedAt.get(1)).toMillis())
                .isGreaterThanOrEqualTo(delayMillis);
    }

    @Test
    void confirmsInTheOwnersLanguage() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withStatus(200)));
        TelegramBotAdapter adapter = adapter(TOKEN);

        adapter.confirmLinked(111L, Language.FR);
        adapter.confirmLinked(111L, Language.EN);

        wireMock.verify(postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withRequestBody(equalToJson(
                        "{\"chat_id\":111,\"text\":\"✅ Cairn est connecté. Les récaps arriveront ici.\"}",
                        true,
                        true)));
        wireMock.verify(postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withRequestBody(equalToJson(
                        "{\"chat_id\":111,\"text\":\"✅ Cairn is connected. Your recaps will arrive here.\"}",
                        true,
                        true)));
    }
}
