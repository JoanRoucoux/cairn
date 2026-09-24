package com.roucoux.cairn.adapter.client.config;

import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * No {@link TransientFailureRetryInterceptor} here: a sent Telegram message is not idempotent, so
 * retrying a 5xx or an I/O failure the way the quote clients do could double-send. The adapter
 * itself retries once, and only on 429 after the delay Telegram asks for.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelegramClientProperties.class)
class TelegramClientConfig {

    @Bean
    RestClient telegramRestClient(TelegramClientProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                // The JDK client's default HTTP/2 tries an h2c upgrade on a plain-HTTP POST with a
                // body, which an HTTP/1.1-only server answers with "EOF reached while reading".
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                        .build(settings))
                .build();
    }
}
