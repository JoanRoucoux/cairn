package com.roucoux.cairn.adapter.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.messaging.adapter.KafkaEventPublisher;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import com.roucoux.cairn.events.PriceUpdatedEvent;
import com.roucoux.cairn.events.Topics;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaMessagingConfigTest {

    @Test
    void wiresAKafkaEventPublisher() {
        MockProducer<String, SpecificRecord> mockProducer = mockProducer();

        PublishEventPort port =
                new KafkaMessagingConfig().publishEventPort(new KafkaTemplate<>(() -> mockProducer), "cairn-api");

        assertThat(port).isInstanceOf(KafkaEventPublisher.class);
    }

    @Test
    void theWiredPublisherSendsTheRecordOnThePricesTopic() {
        MockProducer<String, SpecificRecord> mockProducer = mockProducer();
        UUID instrument = UUID.randomUUID();
        PublishEventPort port =
                new KafkaMessagingConfig().publishEventPort(new KafkaTemplate<>(() -> mockProducer), "cairn-api");

        port.publish(new PriceUpdated(new Quote(
                instrument,
                LocalDate.of(2026, 9, 23),
                new BigDecimal("0.000000120000"),
                "EUR",
                PriceSource.COINGECKO,
                Instant.parse("2026-09-23T13:45:02Z"))));

        ProducerRecord<String, SpecificRecord> sent = mockProducer.history().getFirst();
        assertThat(sent.topic()).isEqualTo(Topics.PRICES);
        assertThat(sent.key()).isEqualTo(instrument.toString());
        assertThat(((PriceUpdatedEvent) sent.value()).getPrice()).isEqualTo(new BigDecimal("0.000000120000"));
    }

    private static MockProducer<String, SpecificRecord> mockProducer() {
        return new MockProducer<>(true, null, new StringSerializer(), (topic, record) -> new byte[0]);
    }
}
