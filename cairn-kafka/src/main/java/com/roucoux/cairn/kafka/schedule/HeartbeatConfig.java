package com.roucoux.cairn.kafka.schedule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class HeartbeatConfig {

    @Bean
    Heartbeat intradayHeartbeat(
            RestClient.Builder restClientBuilder, @Value("${app.kuma.intraday-push-url:}") String url) {
        return new Heartbeat(restClientBuilder, url, "intraday");
    }

    @Bean
    Heartbeat summaryHeartbeat(
            RestClient.Builder restClientBuilder, @Value("${app.kuma.summary-push-url:}") String url) {
        return new Heartbeat(restClientBuilder, url, "summary");
    }
}
