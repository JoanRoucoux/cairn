package com.roucoux.cairn.adapter.messaging.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.messaging.kafka")
public record KafkaMessagingProperties(
        @DefaultValue("cairn.prices") String pricesTopic,
        @DefaultValue("cairn.portfolio") String portfolioTopic) {}
