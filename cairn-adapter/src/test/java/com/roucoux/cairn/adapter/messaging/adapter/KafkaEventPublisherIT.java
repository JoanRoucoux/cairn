package com.roucoux.cairn.adapter.messaging.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.events.PriceUpdatedEvent;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import com.roucoux.cairn.events.Topics;
import com.roucoux.cairn.events.TrustedEventClasses;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaEventPublisherIT {

    private static final String APPLICATION_NAME = "cairn-api";
    private static final String EVENTS_GROUP = "com.roucoux.cairn.events";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);
    private static final String UNREACHABLE_REGISTRY = "http://127.0.0.1:1/apis/registry/v3";

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Container
    static GenericContainer<?> registry = newRegistry();

    @Container
    static GenericContainer<?> emptyRegistry = newRegistry();

    @Container
    static GenericContainer<?> outageRegistry = newRegistry();

    private static final List<DefaultKafkaProducerFactory<String, SpecificRecord>> FACTORIES = new ArrayList<>();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Logger logger = (Logger) LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private KafkaConsumer<String, byte[]> consumer;

    private static GenericContainer<?> newRegistry() {
        return new GenericContainer<>("apicurio/apicurio-registry:3.3.3")
                .withExposedPorts(8080, 9000)
                .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(120)));
    }

    private static String registryUrl(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(8080) + "/apis/registry/v3";
    }

    @BeforeAll
    static void createTopicsAndRegisterSchemas() throws Exception {
        TrustedEventClasses.register();
        try (Admin admin =
                Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                            new NewTopic(Topics.PRICES, 1, (short) 1), new NewTopic(Topics.PORTFOLIO, 1, (short) 1)))
                    .all()
                    .get();
        }
        registerSchemas(registry);
        registerSchemas(outageRegistry);
    }

    @AfterAll
    static void closeFactories() {
        FACTORIES.forEach(DefaultKafkaProducerFactory::destroy);
    }

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        consumer = new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        kafka.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "kafka-event-publisher-it-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest"),
                new StringDeserializer(),
                new ByteArrayDeserializer());
        consumer.subscribe(List.of(Topics.PRICES, Topics.PORTFOLIO));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
        logger.detachAppender(appender);
        logger.setAdditive(true);
    }

    @Test
    @Order(1)
    void publishesAPriceUpdatedEventReadableWithTheRegistry() {
        UUID instrument = UUID.randomUUID();
        KafkaEventPublisher publisher = publisherFor(registryUrl(registry));

        publisher.publish(priceUpdated(instrument));

        ConsumerRecord<String, byte[]> record = pollMatching(Topics.PRICES, instrument.toString());
        PriceUpdatedEvent event = (PriceUpdatedEvent) deserializer(registry).deserialize(Topics.PRICES, record.value());
        assertThat(record.key()).isEqualTo(instrument.toString());
        assertThat(event.getInstrumentId()).isEqualTo(instrument);
        assertThat(event.getAsOf()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(event.getPrice()).isEqualTo(new BigDecimal("0.000000120000"));
        assertThat(event.getCurrency()).isEqualTo("EUR");
        assertThat(event.getPriceSource()).isEqualTo(com.roucoux.cairn.events.PriceSource.COINGECKO);
        assertThat(event.getMetadata().getSource()).isEqualTo(APPLICATION_NAME);
        assertThat(event.getMetadata().getId()).isNotNull();
        assertThat(event.getMetadata().getOccurredAt()).isNotNull();
    }

    @Test
    @Order(2)
    void publishesARefreshCompletedEvent() {
        KafkaEventPublisher publisher = publisherFor(registryUrl(registry));

        publisher.publish(new RefreshCompleted(Set.of(AssetClass.ETF), 7, 1, RefreshTrigger.MANUAL));

        ConsumerRecord<String, byte[]> record = pollMatching(Topics.PORTFOLIO, null);
        RefreshCompletedEvent event =
                (RefreshCompletedEvent) deserializer(registry).deserialize(Topics.PORTFOLIO, record.value());
        assertThat(record.key()).isNull();
        assertThat(event.getAssetClasses()).containsExactly(com.roucoux.cairn.events.AssetClass.ETF);
        assertThat(event.getRefreshed()).isEqualTo(7);
        assertThat(event.getFailed()).isEqualTo(1);
        assertThat(event.getTrigger()).isEqualTo(com.roucoux.cairn.events.RefreshTrigger.MANUAL);
    }

    @Test
    @Order(3)
    void anUnregisteredSchemaIsLoggedAndSwallowed() {
        UUID instrument = UUID.randomUUID();
        KafkaEventPublisher publisher = publisherFor(registryUrl(emptyRegistry));

        assertThatCode(() -> publisher.publish(priceUpdated(instrument))).doesNotThrowAnyException();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertNothingOnTopic(Topics.PRICES, instrument.toString());
    }

    @Test
    @Order(4)
    void aSchemaRegisteredWithOtherContentIsNeverUsed() throws Exception {
        UUID instrument = UUID.randomUUID();
        JsonNode other = JSON.readTree(PriceUpdatedEvent.getClassSchema().toString());
        ((ObjectNode) other)
                .withArray("fields")
                .addObject()
                .put("name", "note")
                .putArray("type")
                .add("null")
                .add("string");
        register(emptyRegistry, "PriceUpdatedEvent", JSON.writeValueAsString(other));
        KafkaEventPublisher publisher = publisherFor(registryUrl(emptyRegistry));

        assertThatCode(() -> publisher.publish(priceUpdated(instrument))).doesNotThrowAnyException();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertNothingOnTopic(Topics.PRICES, instrument.toString());
    }

    @Test
    @Order(5)
    void aLaterPublishSucceedsWithoutRestart() throws Exception {
        UUID instrument = UUID.randomUUID();
        KafkaEventPublisher publisher = publisherFor(registryUrl(emptyRegistry));
        publisher.publish(priceUpdated(UUID.randomUUID()));
        registerSchemas(emptyRegistry);

        publisher.publish(priceUpdated(instrument));

        ConsumerRecord<String, byte[]> record = pollMatching(Topics.PRICES, instrument.toString());
        PriceUpdatedEvent event =
                (PriceUpdatedEvent) deserializer(emptyRegistry).deserialize(Topics.PRICES, record.value());
        assertThat(event.getInstrumentId()).isEqualTo(instrument);
    }

    @Test
    @Order(6)
    void anUnreachableRegistryReturnsWithinThreeSeconds() {
        KafkaEventPublisher publisher = publisherFor(UNREACHABLE_REGISTRY);

        assertTimeout(Duration.ofSeconds(3), () -> publisher.publish(priceUpdated(UUID.randomUUID())));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list).allSatisfy(line -> assertThat(line.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    @Order(7)
    void neverThrowsWhenTheBrokerIsUnreachableEvenAcrossSeveralEvents() {
        Map<String, Object> config = producerConfig(registryUrl(registry));
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1");
        DefaultKafkaProducerFactory<String, SpecificRecord> unreachableFactory =
                new DefaultKafkaProducerFactory<>(config);
        try {
            KafkaEventPublisher unreachable =
                    new KafkaEventPublisher(new KafkaTemplate<>(unreachableFactory), APPLICATION_NAME);

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

    @Test
    @Order(8)
    void aRegistryOutageAfterAFirstPublishDoesNotSlowPublishing() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        KafkaEventPublisher publisher = publisherFor(registryUrl(outageRegistry));
        publisher.publish(priceUpdated(first));
        pollMatching(Topics.PRICES, first.toString());
        outageRegistry.stop();

        assertTimeout(Duration.ofSeconds(3), () -> publisher.publish(priceUpdated(second)));

        assertThat(pollMatching(Topics.PRICES, second.toString()).key()).isEqualTo(second.toString());
        assertThat(appender.list).isEmpty();
    }

    private static PriceUpdated priceUpdated(UUID instrument) {
        return new PriceUpdated(new Quote(
                instrument,
                LocalDate.of(2026, 10, 23),
                new BigDecimal("0.000000120000"),
                "EUR",
                PriceSource.COINGECKO,
                Instant.parse("2026-10-23T17:00:00Z")));
    }

    private static Map<String, Object> producerConfig(String registryUrl) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 250);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        config.put("apicurio.registry.url", registryUrl);
        config.put(
                "apicurio.registry.artifact-resolver-strategy",
                "io.apicurio.registry.serde.avro.strategy.RecordIdStrategy");
        config.put("apicurio.registry.auto-register", false);
        config.put("apicurio.registry.retry-count", 0);
        config.put("apicurio.registry.retry-backoff-ms", 100);
        return config;
    }

    private static KafkaEventPublisher publisherFor(String registryUrl) {
        DefaultKafkaProducerFactory<String, SpecificRecord> factory =
                new DefaultKafkaProducerFactory<>(producerConfig(registryUrl));
        FACTORIES.add(factory);
        return new KafkaEventPublisher(new KafkaTemplate<>(factory), APPLICATION_NAME);
    }

    private static AvroKafkaDeserializer<SpecificRecord> deserializer(GenericContainer<?> container) {
        AvroKafkaDeserializer<SpecificRecord> deserializer = new AvroKafkaDeserializer<>();
        deserializer.configure(
                Map.of(
                        "apicurio.registry.url",
                        registryUrl(container),
                        "apicurio.registry.use-specific-avro-reader",
                        true),
                false);
        return deserializer;
    }

    private static void registerSchemas(GenericContainer<?> container) throws Exception {
        register(
                container,
                "PriceUpdatedEvent",
                PriceUpdatedEvent.getClassSchema().toString());
        register(
                container,
                "RefreshCompletedEvent",
                RefreshCompletedEvent.getClassSchema().toString());
    }

    private static void register(GenericContainer<?> container, String artifactId, String schema) throws Exception {
        Map<String, Object> body = Map.of(
                "artifactId",
                artifactId,
                "artifactType",
                "AVRO",
                "firstVersion",
                Map.of("content", Map.of("content", schema, "contentType", "application/json")));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        registryUrl(container) + "/groups/" + EVENTS_GROUP + "/artifacts?ifExists=CREATE_VERSION"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }

    private ConsumerRecord<String, byte[]> pollMatching(String topic, String key) {
        return pollUntil(topic, record -> key == null ? record.key() == null : key.equals(record.key()), POLL_TIMEOUT);
    }

    private void assertNothingOnTopic(String topic, String key) {
        assertThatCode(() -> {
                    ConsumerRecord<String, byte[]> record =
                            pollUntil(topic, found -> key.equals(found.key()), Duration.ofSeconds(3));
                    throw new AssertionError("unexpected record " + record.key());
                })
                .isInstanceOf(NoRecord.class);
    }

    private ConsumerRecord<String, byte[]> pollUntil(
            String topic, Predicate<ConsumerRecord<String, byte[]>> matches, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, byte[]> polled = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, byte[]> record : polled) {
                if (record.topic().equals(topic) && matches.test(record)) {
                    return record;
                }
            }
        }
        throw new NoRecord("no matching record polled on topic " + topic + " within " + timeout);
    }

    private static final class NoRecord extends AssertionError {
        NoRecord(String message) {
            super(message);
        }
    }
}
