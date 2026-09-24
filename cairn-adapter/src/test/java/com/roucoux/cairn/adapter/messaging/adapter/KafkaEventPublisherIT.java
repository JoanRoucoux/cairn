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
import com.roucoux.cairn.domain.model.event.ValuationRecorded;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
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

@Testcontainers
class KafkaEventPublisherIT {

    private static final UUID INSTRUMENT = UUID.randomUUID();
    private static final String APPLICATION_NAME = "cairn-api";
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
        json = KafkaEventPublisher.eventMapper();
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
        publisher = new KafkaEventPublisher(kafkaTemplate, PROPERTIES, APPLICATION_NAME);
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

        ConsumerRecord<String, String> record =
                pollMatching(PROPERTIES.pricesTopic(), value -> value.contains("2396.17"));
        JsonNode envelope = json.readTree(record.value());
        JsonNode data = envelope.get("data");

        assertThat(record.key()).isEqualTo(INSTRUMENT.toString());
        assertThat(envelope.get("type").asText()).isEqualTo("price.updated");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("id").asText()).isNotBlank();
        assertThat(envelope.get("source").asText()).isEqualTo(APPLICATION_NAME);
        assertThatCode(() -> Instant.parse(envelope.get("occurredAt").asText())).doesNotThrowAnyException();

        assertThat(data.get("instrumentId").asText()).isEqualTo(INSTRUMENT.toString());
        assertThat(data.get("asOf").asText()).isEqualTo("2026-09-23");
        assertThat(data.get("price").decimalValue()).isEqualByComparingTo("2396.17");
        assertThat(data.get("currency").asText()).isEqualTo("EUR");
        assertThat(data.get("priceSource").asText()).isEqualTo("COINGECKO");
        assertThat(data.has("fetchedAt")).isFalse();
    }

    @Test
    void writesATinyPriceAsPlainDecimalNeverInScientificNotation() {
        publisher.publish(new PriceUpdated(new Quote(
                INSTRUMENT,
                LocalDate.of(2026, 9, 23),
                new BigDecimal("0.000000120000"),
                "EUR",
                PriceSource.COINGECKO,
                Instant.parse("2026-09-23T13:45:02Z"))));

        ConsumerRecord<String, String> record =
                pollMatching(PROPERTIES.pricesTopic(), value -> value.contains("0.000000120000"));

        // A literal match on the plain-decimal form already rules out scientific notation:
        // checking the absence of "e-7" instead would be a false positive against a random
        // UUID elsewhere in the envelope that happens to contain that substring.
        assertThat(record.value()).contains("0.000000120000");
    }

    @Test
    void publishesTheEndOfARefreshOnThePortfolioTopicWithNoKey() {
        publisher.publish(new RefreshCompleted(Set.of(AssetClass.ETF), 7, 1, RefreshTrigger.MANUAL));

        ConsumerRecord<String, String> record =
                pollMatching(PROPERTIES.portfolioTopic(), value -> value.contains("\"refreshed\":7"));
        JsonNode envelope = json.readTree(record.value());
        JsonNode data = envelope.get("data");

        assertThat(record.key()).isNull();
        assertThat(envelope.get("type").asText()).isEqualTo("refresh.completed");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(data.get("assetClasses")).extracting(JsonNode::asText).containsExactly("ETF");
        assertThat(data.get("refreshed").asInt()).isEqualTo(7);
        assertThat(data.get("failed").asInt()).isEqualTo(1);
        assertThat(data.get("trigger").asText()).isEqualTo("MANUAL");
    }

    @Test
    void publishesARecordedValuationOnThePortfolioTopicWithNoKey() {
        publisher.publish(new ValuationRecorded(
                Instant.parse("2026-09-24T13:47:00Z"), new BigDecimal("25950.0000"), new BigDecimal("125.5000")));

        ConsumerRecord<String, String> record =
                pollMatching(PROPERTIES.portfolioTopic(), value -> value.contains("25950"));
        JsonNode envelope = json.readTree(record.value());
        JsonNode data = envelope.get("data");

        assertThat(record.key()).isNull();
        assertThat(envelope.get("type").asText()).isEqualTo("valuation.recorded");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(data.get("at").asText()).isEqualTo("2026-09-24T13:47:00Z");
        assertThat(data.get("totalEur").decimalValue()).isEqualByComparingTo("25950.0000");
        assertThat(data.get("dayChangeEur").decimalValue()).isEqualByComparingTo("125.5000");
    }

    @Test
    void neverThrowsWhenTheBrokerIsUnreachableEvenAcrossSeveralEvents() {
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
                250));
        try {
            KafkaEventPublisher unreachable =
                    new KafkaEventPublisher(new KafkaTemplate<>(unreachableFactory), PROPERTIES, APPLICATION_NAME);

            long start = System.nanoTime();
            assertThatCode(() -> {
                        for (int i = 0; i < 5; i++) {
                            unreachable.publish(new RefreshCompleted(Set.of(), 0, 0, RefreshTrigger.MANUAL));
                        }
                    })
                    .doesNotThrowAnyException();

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        } finally {
            unreachableFactory.destroy();
        }
    }

    // auto.offset.reset=earliest means every test's fresh consumer group re-reads every record
    // any earlier test in this class already published to the same topic, so matching by topic
    // alone would return a stale record instead of the one this test just published.
    private ConsumerRecord<String, String> pollMatching(String topic, Predicate<String> valueMatches) {
        long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : polled) {
                if (record.topic().equals(topic) && valueMatches.test(record.value())) {
                    return record;
                }
            }
        }
        throw new AssertionError("no matching record polled on topic " + topic + " within " + POLL_TIMEOUT);
    }
}
