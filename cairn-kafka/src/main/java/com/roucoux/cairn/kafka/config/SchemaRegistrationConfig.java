package com.roucoux.cairn.kafka.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class SchemaRegistrationConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Bean
    RestClient registryRestClient(
            RestClient.Builder builder, @Value("${spring.kafka.properties.apicurio.registry.url}") String url) {
        return builder.baseUrl(url).requestFactory(requestFactory()).build();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .build());
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    @Bean
    SchemaRegistrar schemaRegistrar(RestClient registryRestClient) {
        return new SchemaRegistrar(registryRestClient);
    }

    @Bean
    SmartInitializingSingleton schemaRegistration(SchemaRegistrar schemaRegistrar) {
        return schemaRegistrar::registerAll;
    }
}
