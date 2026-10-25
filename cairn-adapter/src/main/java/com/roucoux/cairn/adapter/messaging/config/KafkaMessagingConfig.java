package com.roucoux.cairn.adapter.messaging.config;

import com.roucoux.cairn.adapter.messaging.adapter.KafkaEventPublisher;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import com.roucoux.cairn.events.TrustedEventClasses;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
class KafkaMessagingConfig {

    @Bean
    PublishEventPort publishEventPort(
            KafkaTemplate<String, SpecificRecord> kafkaTemplate,
            @Value("${spring.application.name}") String applicationName) {
        TrustedEventClasses.register();
        return new KafkaEventPublisher(kafkaTemplate, applicationName);
    }
}
