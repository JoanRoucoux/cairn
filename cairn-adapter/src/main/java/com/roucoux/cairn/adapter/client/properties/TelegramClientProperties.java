package com.roucoux.cairn.adapter.client.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code botToken}/{@code chatId} are not validated at binding time: {@code cairn-api} and
 * {@code cairn-batch} must start without {@code TELEGRAM_*} set, so the check happens only when
 * {@code TelegramNotificationAdapter} actually sends a message.
 */
@ConfigurationProperties(prefix = "app.telegram")
public record TelegramClientProperties(
        String baseUrl, String botToken, String chatId, Duration connectTimeout, Duration readTimeout) {}
