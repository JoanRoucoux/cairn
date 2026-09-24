package com.roucoux.cairn.adapter.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.messaging.adapter.KafkaEventPublisher;
import com.roucoux.cairn.adapter.messaging.properties.KafkaMessagingProperties;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaMessagingConfigTest {

    private static final KafkaMessagingProperties PROPERTIES =
            new KafkaMessagingProperties("cairn.prices", "cairn.portfolio");

    @Test
    void wiresAKafkaEventPublisher() {
        MockProducer<String, String> mockProducer =
                new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());

        PublishEventPort port = new KafkaMessagingConfig()
                .publishEventPort(new KafkaTemplate<>(() -> mockProducer), PROPERTIES, "cairn-api");

        assertThat(port).isInstanceOf(KafkaEventPublisher.class);
    }

    @Test
    void theWiredPublisherWritesATinyPriceAsPlainDecimal() {
        MockProducer<String, String> mockProducer =
                new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
        PublishEventPort port = new KafkaMessagingConfig()
                .publishEventPort(new KafkaTemplate<>(() -> mockProducer), PROPERTIES, "cairn-api");

        port.publish(new PriceUpdated(new Quote(
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 23),
                new BigDecimal("0.000000120000"),
                "EUR",
                PriceSource.COINGECKO,
                Instant.parse("2026-09-23T13:45:02Z"))));

        ProducerRecord<String, String> sent = mockProducer.history().getFirst();
        assertThat(sent.value()).contains("0.000000120000");
    }
}
