package com.roucoux.cairn;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.IntradayValuationJpaRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots the full worker context, exactly like {@code KafkaWorkerApplicationIT}, and exercises
 * {@code ValuationConsumer} end to end: a {@code refresh.completed} envelope sent on
 * {@code cairn.portfolio} must produce a row in {@code intraday_valuations} and a
 * {@code valuation.recorded} envelope back on the same topic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml")
@Testcontainers
class ValuationRoundTripIT {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(15);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private IntradayValuationJpaRepository valuations;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void subscribeToThePortfolioTopic() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "valuation-round-trip-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class));
        consumer.subscribe(List.of("cairn.portfolio"));
    }

    @AfterEach
    void closeConsumer() {
        consumer.close();
    }

    @Test
    void aRefreshCompletedEnvelopeIsTurnedIntoARecordedValuation() {
        String envelope = """
                {"id":"22222222-2222-2222-2222-222222222222","type":"refresh.completed","version":1,\
                "occurredAt":"2026-09-24T09:31:00Z","source":"cairn-api",\
                "data":{"assetClasses":[],"refreshed":0,"failed":0,"trigger":"MANUAL"}}\
                """;

        awaitPublicationOf(envelope);

        ConsumerRecord<String, String> published =
                pollMatching(value -> value.contains("\"type\":\"valuation.recorded\""));
        JsonNode envelopeReceived = JSON_MAPPER.readTree(published.value());

        assertThat(envelopeReceived.get("data").get("totalEur").decimalValue()).isEqualByComparingTo("0");
        assertThat(valuations.findAll()).isNotEmpty();
    }

    // ValuationConsumer's listener container needs its partition assignment before "latest" ever
    // sees a record: a single send right after context start-up can race that assignment, so the
    // send is repeated until the worker's own valuation.recorded echo proves it was consumed.
    private void awaitPublicationOf(String envelope) {
        long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            kafkaTemplate.send("cairn.portfolio", envelope);
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : polled) {
                if (record.value().contains("\"type\":\"valuation.recorded\"")) {
                    return;
                }
            }
        }
    }

    private ConsumerRecord<String, String> pollMatching(Predicate<String> valueMatches) {
        long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : polled) {
                if (valueMatches.test(record.value())) {
                    return record;
                }
            }
        }
        throw new AssertionError("no matching record polled on cairn.portfolio within " + POLL_TIMEOUT);
    }
}
