package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.DailySummary;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.port.out.SendNotificationPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Nothing is escaped: free text (an instrument name) needs <, > and & escaped or Telegram answers 400. */
@Component
public class TelegramNotificationAdapter implements SendNotificationPort {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE);
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);
    private static final Map<AccountType, String> ENVELOPE_LABELS = envelopeLabels();

    private final RestClient client;
    private final TelegramClientProperties properties;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;

    @Autowired
    public TelegramNotificationAdapter(
            @Qualifier("telegramRestClient") RestClient client, TelegramClientProperties properties) {
        this(client, properties, new ObjectMapper(), Sleeper.THREAD);
    }

    TelegramNotificationAdapter(
            RestClient client, TelegramClientProperties properties, ObjectMapper objectMapper, Sleeper sleeper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
    }

    @Override
    public void send(DailySummary summary) {
        requireConfigured();
        String text = format(summary);
        try {
            post(text);
        } catch (TooManyRequests tooManyRequests) {
            sleeper.sleep(tooManyRequests.retryAfter());
            try {
                post(text);
            } catch (RuntimeException secondFailure) {
                throw new NotificationDeliveryException("Telegram call failed after one retry on 429");
            }
        } catch (RestClientException failure) {
            throw new NotificationDeliveryException("Telegram call failed");
        }
    }

    private void requireConfigured() {
        if (isBlank(properties.botToken()) || isBlank(properties.chatId())) {
            throw new NotificationDeliveryException("Telegram is not configured");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void post(String text) {
        try {
            client.post()
                    .uri("/bot{token}/sendMessage", properties.botToken())
                    .body(new SendMessageRequest(properties.chatId(), "HTML", text))
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

    private static String format(DailySummary summary) {
        Performance performance = summary.performance();
        BigDecimal change = roundedEuros(performance.change());
        StringBuilder message = new StringBuilder()
                .append("📊 <b>Cairn · ")
                .append(capitalize(DATE_FORMAT.format(summary.date())))
                .append("</b>\n\n💰 Patrimoine  <b>")
                .append(grouped(roundedEuros(performance.total()), "#,##0"))
                .append(" €</b>\n")
                .append(change.signum() < 0 ? "📉" : "📈")
                .append(" Jour  <b>")
                .append(signed(change, "#,##0"))
                .append(" €</b>");
        performance
                .changeRatio()
                .ifPresent(ratio -> message.append("  (")
                        .append(signed(percent(ratio), "#,##0.00"))
                        .append(" %)"));
        if (!performance.byEnvelope().isEmpty()) {
            message.append("\n\n<pre>")
                    .append(envelopeTable(performance.byEnvelope()))
                    .append("</pre>");
        }
        return message.toString();
    }

    private static String envelopeTable(List<EnvelopePerformance> envelopes) {
        List<List<String>> rows = envelopes.stream()
                .map(envelope -> {
                    BigDecimal change = roundedEuros(envelope.change());
                    return List.of(
                            dot(change),
                            ENVELOPE_LABELS.get(envelope.accountType()),
                            grouped(roundedEuros(envelope.value()), "#,##0"),
                            signed(change, "#,##0"),
                            envelope.changeRatio()
                                    .map(ratio -> signed(percent(ratio), "#,##0.00") + "%")
                                    .orElse(""));
                })
                .toList();
        int labelWidth = width(rows, 1);
        int valueWidth = width(rows, 2);
        int changeWidth = width(rows, 3);
        int ratioWidth = width(rows, 4);
        return rows.stream()
                .map(row -> (row.get(0) + " " + padRight(row.get(1), labelWidth) + "  "
                                + padLeft(row.get(2), valueWidth) + "  " + padLeft(row.get(3), changeWidth) + "  "
                                + padLeft(row.get(4), ratioWidth))
                        .stripTrailing())
                .collect(Collectors.joining("\n"));
    }

    private static String dot(BigDecimal roundedChange) {
        return switch (roundedChange.signum()) {
            case 1 -> "🟢";
            case -1 -> "🔴";
            default -> "⚪";
        };
    }

    private static int width(List<List<String>> rows, int column) {
        return rows.stream().mapToInt(row -> row.get(column).length()).max().orElse(0);
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return " ".repeat(width - text.length()) + text;
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase(Locale.FRANCE) + text.substring(1);
    }

    private static BigDecimal roundedEuros(Money money) {
        return money.amount().setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String signed(BigDecimal rounded, String pattern) {
        String digits = grouped(rounded.abs(), pattern);
        return switch (rounded.signum()) {
            case 1 -> "+" + digits;
            case -1 -> "-" + digits;
            default -> digits;
        };
    }

    private static String grouped(BigDecimal amount, String pattern) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat(pattern, symbols).format(amount);
    }

    private static Map<AccountType, String> envelopeLabels() {
        Map<AccountType, String> labels = new EnumMap<>(AccountType.class);
        labels.put(AccountType.PEA, "PEA");
        labels.put(AccountType.PEA_PME, "PEA-PME");
        labels.put(AccountType.CTO, "CTO");
        labels.put(AccountType.PER, "PER");
        labels.put(AccountType.PEE, "PEE");
        labels.put(AccountType.LIFE_INSURANCE, "Assu. vie");
        labels.put(AccountType.SAVINGS, "Livrets");
        labels.put(AccountType.CRYPTO, "Crypto");
        return labels;
    }

    private record SendMessageRequest(
            @JsonProperty("chat_id") String chatId,
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
