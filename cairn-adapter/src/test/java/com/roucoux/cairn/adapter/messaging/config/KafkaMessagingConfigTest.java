package com.roucoux.cairn.adapter.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.messaging.adapter.KafkaEventPublisher;
import com.roucoux.cairn.adapter.messaging.properties.KafkaMessagingProperties;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class KafkaMessagingConfigTest {

    @Test
    void wiresAKafkaEventPublisher() {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class)));

        PublishEventPort port = new KafkaMessagingConfig()
                .publishEventPort(
                        kafkaTemplate,
                        JsonMapper.builder().build(),
                        new KafkaMessagingProperties("cairn.prices", "cairn.portfolio"),
                        "cairn-api");

        assertThat(port).isInstanceOf(KafkaEventPublisher.class);
    }

    @Test
    void enablesPlainBigDecimalOnTheSharedJsonMapper() {
        JsonMapperBuilderCustomizer customizer = new KafkaMessagingConfig().plainBigDecimalJsonMapperCustomizer();

        JsonMapper.Builder builder = JsonMapper.builder();
        customizer.customize(builder);
        JsonMapper mapper = builder.build();

        assertThat(mapper.writeValueAsString(new BigDecimal("0.000000120000"))).isEqualTo("0.000000120000");
    }
}
