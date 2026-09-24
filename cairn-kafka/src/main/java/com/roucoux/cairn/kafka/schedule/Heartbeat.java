package com.roucoux.cairn.kafka.schedule;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Pings the Uptime Kuma push monitor after a successful intraday refresh, the same convention
 * {@code run-batch.sh} uses for the batch schedule. The URL is optional: an application without
 * {@code KUMA_PUSH_INTRADAY} set simply never calls out.
 */
@Component
class Heartbeat {

    private static final Logger log = LoggerFactory.getLogger(Heartbeat.class);

    private final RestClient restClient;
    private final String url;

    Heartbeat(RestClient.Builder restClientBuilder, @Value("${app.kuma.intraday-push-url:}") String url) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        this.restClient = restClientBuilder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        this.url = url;
    }

    void ping() {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Intraday heartbeat ping failed", e);
        }
    }
}
