package com.roucoux.cairn.adapter.messaging.config;

import com.roucoux.cairn.adapter.messaging.adapter.KafkaEventPublisher;
import com.roucoux.cairn.adapter.messaging.properties.KafkaMessagingProperties;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaMessagingProperties.class)
class KafkaMessagingConfig {

    @Bean
    PublishEventPort publishEventPort(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            KafkaMessagingProperties properties,
            @Value("${spring.application.name}") String applicationName) {
        return new KafkaEventPublisher(kafkaTemplate, jsonMapper, properties, applicationName);
    }

    // Applied to the single app-wide JsonMapper bean, not only the Kafka envelope: a BigDecimal
    // price in scientific notation would be as wrong on a REST response as on the wire here.
    @Bean
    JsonMapperBuilderCustomizer plainBigDecimalJsonMapperCustomizer() {
        return builder -> builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }
}
