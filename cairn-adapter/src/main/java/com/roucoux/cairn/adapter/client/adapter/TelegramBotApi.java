package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class TelegramBotApi {

    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

    private final RestClient client;
    private final TelegramClientProperties properties;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;

    @Autowired
    TelegramBotApi(@Qualifier("telegramRestClient") RestClient client, TelegramClientProperties properties) {
        this(client, properties, new ObjectMapper(), Sleeper.THREAD);
    }

    TelegramBotApi(RestClient client, TelegramClientProperties properties, ObjectMapper objectMapper, Sleeper sleeper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
    }

    boolean configured() {
        return !isBlank(properties.botToken());
    }

    void sendMessage(long chatId, String html) {
        requireConfigured();
        try {
            post(chatId, html);
        } catch (TooManyRequests tooManyRequests) {
            sleeper.sleep(tooManyRequests.retryAfter());
            try {
                post(chatId, html);
            } catch (RuntimeException secondFailure) {
                throw new NotificationDeliveryException("Telegram call failed after one retry on 429");
            }
        } catch (RestClientException failure) {
            throw new NotificationDeliveryException("Telegram call failed");
        }
    }

    JsonNode getMe() {
        requireConfigured();
        try {
            String body = client.get()
                    .uri("/bot{token}/getMe", properties.botToken())
                    .retrieve()
                    .body(String.class);
            return result(body);
        } catch (RestClientException failure) {
            throw new NotificationDeliveryException("Telegram call failed");
        }
    }

    JsonNode getUpdates(Long offset, int limit) {
        requireConfigured();
        Map<String, Object> request = new LinkedHashMap<>();
        if (offset != null) {
            request.put("offset", offset);
        }
        request.put("limit", limit);
        request.put("timeout", 0);
        request.put("allowed_updates", List.of("message"));
        try {
            String body = client.post()
                    .uri("/bot{token}/getUpdates", properties.botToken())
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return result(body);
        } catch (RestClientException failure) {
            throw new NotificationDeliveryException("Telegram call failed");
        }
    }

    private JsonNode result(String body) {
        try {
            JsonNode result = objectMapper.readTree(body).path("result");
            if (result.isMissingNode() || result.isNull()) {
                throw new NotificationDeliveryException("Telegram answered without a result");
            }
            return result;
        } catch (NotificationDeliveryException failure) {
            throw failure;
        } catch (Exception unparsable) {
            throw new NotificationDeliveryException("Telegram answered with an unreadable body");
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new NotificationDeliveryException("Telegram is not configured");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void post(long chatId, String html) {
        try {
            client.post()
                    .uri("/bot{token}/sendMessage", properties.botToken())
                    .body(new SendMessageRequest(chatId, "HTML", html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.TooManyRequests tooManyRequests) {
            throw new TooManyRequests(retryAfter(tooManyRequests));
        }
    }

    private Duration retryAfter(RestClientResponseException failure) {
        try {
            JsonNode body = objectMapper.readTree(failure.getResponseBodyAsString());
            long seconds = body.path("parameters").path("retry_after").asLong(0);
            Duration wait = Duration.ofSeconds(seconds);
            return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
        } catch (Exception unparsable) {
            return Duration.ZERO;
        }
    }

    private record SendMessageRequest(
            @JsonProperty("chat_id") long chatId,
            @JsonProperty("parse_mode") String parseMode,
            String text) {}

    private static final class TooManyRequests extends RuntimeException {
        private final Duration retryAfter;

        TooManyRequests(Duration retryAfter) {
            super(null, null, false, false);
            this.retryAfter = retryAfter;
        }

        Duration retryAfter() {
            return retryAfter;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        Sleeper THREAD = duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };

        void sleep(Duration duration);
    }
}
