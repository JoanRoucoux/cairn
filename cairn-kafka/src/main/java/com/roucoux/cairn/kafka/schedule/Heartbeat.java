package com.roucoux.cairn.kafka.schedule;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

/**
 * Pings an Uptime Kuma push monitor after a successful scheduled run, the same convention {@code
 * run-batch.sh} uses for the batch schedule. {@code HeartbeatConfig} wires one instance per
 * monitor (intraday refresh, daily summary). The URL is optional: an application without the
 * corresponding {@code KUMA_PUSH_*} set simply never calls out.
 */
class Heartbeat {

    private static final Logger log = LoggerFactory.getLogger(Heartbeat.class);

    private final RestClient restClient;
    private final String url;
    private final String monitorName;

    Heartbeat(RestClient.Builder restClientBuilder, String url, String monitorName) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        this.restClient = restClientBuilder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        this.url = url;
        this.monitorName = monitorName;
    }

    void ping() {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("{} heartbeat ping failed", monitorName, e);
        }
    }
}
