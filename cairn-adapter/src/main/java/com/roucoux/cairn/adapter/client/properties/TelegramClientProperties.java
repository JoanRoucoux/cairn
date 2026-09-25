package com.roucoux.cairn.adapter.client.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telegram")
public record TelegramClientProperties(
        String baseUrl, String botToken, String chatId, Duration connectTimeout, Duration readTimeout) {}
