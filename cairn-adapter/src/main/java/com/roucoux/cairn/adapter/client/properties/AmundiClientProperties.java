package com.roucoux.cairn.adapter.client.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.client.amundi")
public record AmundiClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout, String userAgent) {}
