package com.roucoux.cairn.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class TopicsConfig {

    private static final int PARTITIONS = 1;
    private static final short REPLICATION_FACTOR = 1;
    private static final String RETENTION_MS = "604800000";

    @Bean
    NewTopic pricesTopic() {
        return topic("cairn.prices");
    }

    @Bean
    NewTopic portfolioTopic() {
        return topic("cairn.portfolio");
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_MS)
                .build();
    }
}
