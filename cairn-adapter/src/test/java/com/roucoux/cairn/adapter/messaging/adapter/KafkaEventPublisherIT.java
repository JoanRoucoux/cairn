package com.roucoux.cairn.adapter.messaging.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.roucoux.cairn.adapter.messaging.properties.KafkaMessagingProperties;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the {@code KafkaTemplate} straight from {@code KafkaMessagingConfig}'s wiring, without a
 * Spring context, the same way the client slice's {@code *ConfigTest} classes call their
 * {@code @Bean} methods directly — this module has no Spring Boot application of its own.
 */
@Testcontainers
class KafkaEventPublisherIT {

    private static final UUID INSTRUMENT = UUID.randomUUID();
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);
    private static final KafkaMessagingProperties PROPERTIES =
            new KafkaMessagingProperties("cairn.prices", "cairn.portfolio");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    private static JsonMapper json;
    private static DefaultKafkaProducerFactory<String, String> producerFactory;
    private static KafkaTemplate<String, String> kafkaTemplate;

    private KafkaEventPublisher publisher;
    private KafkaConsumer<String, String> consumer;

    @BeforeAll
    static void startBrokerAndCreateTopics() throws Exception {
        json = JsonMapper.builder().build();
        producerFactory = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
        try (Admin admin =
                Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                            new NewTopic(PROPERTIES.pricesTopic(), 1, (short) 1),
                            new NewTopic(PROPERTIES.portfolioTopic(), 1, (short) 1)))
                    .all()
                    .get();
        }
    }

    @AfterAll
    static void closeTemplate() {
        producerFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(kafkaTemplate, json, PROPERTIES);
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "kafka-event-publisher-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class));
        consumer.subscribe(List.of(PROPERTIES.pricesTopic(), PROPERTIES.portfolioTopic()));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void publishesAPriceUpdateOnThePricesTopicKeyedByInstrument() {
        publisher.publish(new PriceUpdated(new Quote(
                INSTRUMENT,
                LocalDate.of(2026, 9, 23),
                new BigDecimal("2396.17"),
                "EUR",
                PriceSource.COINGECKO,
                Instant.parse("2026-09-23T13:45:02Z"))));

        ConsumerRecord<String, String> record = pollOne(PROPERTIES.pricesTopic());

        assertThat(record.key()).isEqualTo(INSTRUMENT.toString());
        JsonNode envelope = json.readTree(record.value());
        assertThat(envelope.get("type").asText()).isEqualTo("price.updated");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("data").get("price").decimalValue()).isEqualByComparingTo("2396.17");
        assertThat(envelope.get("id").asText()).isNotBlank();
    }

    @Test
    void publishesTheEndOfARefreshOnThePortfolioTopic() {
        publisher.publish(new RefreshCompleted(Set.of(AssetClass.ETF), 7, 1, RefreshTrigger.MANUAL));

        ConsumerRecord<String, String> record = pollOne(PROPERTIES.portfolioTopic());

        JsonNode envelope = json.readTree(record.value());
        assertThat(envelope.get("type").asText()).isEqualTo("refresh.completed");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("data").get("trigger").asText()).isEqualTo("MANUAL");
        assertThat(envelope.get("data").get("refreshed").asInt()).isEqualTo(7);
        assertThat(envelope.get("data").get("failed").asInt()).isEqualTo(1);
    }

    @Test
    void neverThrowsWhenTheBrokerIsUnreachable() {
        // Left open, the producer's background thread keeps retrying forever and the JVM never
        // exits, so Surefire has to kill the whole fork after its 30s grace period.
        DefaultKafkaProducerFactory<String, String> unreachableFactory = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "127.0.0.1:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG,
                1000));
        try {
            KafkaEventPublisher unreachable =
                    new KafkaEventPublisher(new KafkaTemplate<>(unreachableFactory), json, PROPERTIES);

            long start = System.nanoTime();
            assertThatCode(() -> unreachable.publish(new RefreshCompleted(Set.of(), 0, 0, RefreshTrigger.MANUAL)))
                    .doesNotThrowAnyException();

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        } finally {
            unreachableFactory.destroy();
        }
    }

    private ConsumerRecord<String, String> pollOne(String topic) {
        long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : polled) {
                if (record.topic().equals(topic)) {
                    return record;
                }
            }
        }
        throw new AssertionError("no record polled on topic " + topic + " within " + POLL_TIMEOUT);
    }
}
