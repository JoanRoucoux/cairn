package com.roucoux.cairn.adapter.client.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.client.coingecko")
public record CoinGeckoClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
