package com.roucoux.cairn.adapter.client.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.client.market-data")
public record MarketDataClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
