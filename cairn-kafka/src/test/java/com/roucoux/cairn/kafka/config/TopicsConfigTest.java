package com.roucoux.cairn.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;

/** Bean-wiring test, no Spring context: calls the {@code @Bean} methods directly. */
class TopicsConfigTest {

    private final TopicsConfig config = new TopicsConfig();

    @Test
    void declaresThePricesTopic() {
        NewTopic topic = config.pricesTopic();

        assertThat(topic.name()).isEqualTo("cairn.prices");
        assertThat(topic.numPartitions()).isEqualTo(1);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
        assertThat(topic.configs()).containsEntry(TopicConfig.RETENTION_MS_CONFIG, "604800000");
    }

    @Test
    void declaresThePortfolioTopic() {
        NewTopic topic = config.portfolioTopic();

        assertThat(topic.name()).isEqualTo("cairn.portfolio");
        assertThat(topic.numPartitions()).isEqualTo(1);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
        assertThat(topic.configs()).containsEntry(TopicConfig.RETENTION_MS_CONFIG, "604800000");
    }
}
