package com.roucoux.cairn.adapter.client.config;

import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** No TransientFailureRetryInterceptor: retrying a send that may have gone through would post it twice. */
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
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                        .build(settings))
                .build();
    }
}
