package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.DailySummary;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Money;
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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Outbound adapter: posts the daily summary to Telegram, formatted in French. The message is plain
 * text (no {@code parse_mode}), so there is no Markdown/HTML escaping to do.
 */
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
                    .body(new SendMessageRequest(properties.chatId(), text))
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
        StringBuilder message = new StringBuilder();
        message.append("Cairn, resume du ")
                .append(DATE_FORMAT.format(summary.date()))
                .append("\n\n");
        message.append("Patrimoine : ")
                .append(formatUnsignedAmount(summary.performance().total()))
                .append(" EUR\n");
        message.append("Jour : ")
                .append(formatSignedAmount(summary.performance().change()))
                .append(" EUR (")
                .append(formatRatio(summary.performance().changeRatio()))
                .append(")");
        List<EnvelopePerformance> envelopes = summary.performance().byEnvelope();
        for (int i = 0; i < envelopes.size(); i++) {
            message.append(i == 0 ? "\n\n" : "\n").append(formatEnvelope(envelopes.get(i)));
        }
        return message.toString();
    }

    private static String formatEnvelope(EnvelopePerformance envelope) {
        String label = ENVELOPE_LABELS.get(envelope.accountType());
        String amount = formatSignedAmount(envelope.change()) + " EUR";
        return envelope.changeRatio().isEmpty()
                ? label + " : " + amount
                : label + " : " + amount + " (" + formatRatio(envelope.changeRatio()) + ")";
    }

    private static String formatSignedAmount(Money money) {
        return signedDecimalFormat("#,##0").format(money.amount().setScale(0, RoundingMode.HALF_UP));
    }

    private static String formatUnsignedAmount(Money money) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols).format(money.amount().setScale(0, RoundingMode.HALF_UP));
    }

    private static String formatRatio(Optional<BigDecimal> ratio) {
        BigDecimal percent =
                ratio.orElseThrow().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        return signedDecimalFormat("#,##0.00").format(percent) + " %";
    }

    /**
     * The euro symbol never appears in the message: {@code Money}'s currency is always EUR here and
     * "EUR" is spelled out. Grouping uses a plain space rather than the narrow no-break space the
     * French locale defaults to, so the message renders identically whether or not a client font
     * supports {@code  }.
     */
    private static DecimalFormat signedDecimalFormat(String pattern) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setMinusSign('-');
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setPositivePrefix("+");
        format.setNegativePrefix("-");
        return format;
    }

    private static Map<AccountType, String> envelopeLabels() {
        Map<AccountType, String> labels = new EnumMap<>(AccountType.class);
        labels.put(AccountType.PEA, "PEA");
        labels.put(AccountType.PEA_PME, "PEA-PME");
        labels.put(AccountType.CTO, "CTO");
        labels.put(AccountType.PER, "PER");
        labels.put(AccountType.PEE, "PEE");
        labels.put(AccountType.LIFE_INSURANCE, "Assurance vie");
        labels.put(AccountType.SAVINGS, "Livrets");
        labels.put(AccountType.CRYPTO, "Crypto");
        return labels;
    }

    private record SendMessageRequest(
            @JsonProperty("chat_id") String chatId, String text) {}

    /** Carries the delay to wait before the single retry a 429 gets; never logged, never a cause. */
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
