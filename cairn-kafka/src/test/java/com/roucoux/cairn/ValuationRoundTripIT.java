package com.roucoux.cairn;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.IntradayValuationJpaRepository;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml")
@Testcontainers
class ValuationRoundTripIT {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(15);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private IntradayValuationJpaRepository valuations;

    @Test
    void aRefreshCompletedEnvelopeIsTurnedIntoARecordedValuation() {
        awaitTheCairnValuationContainerToHaveAssignedPartitions();

        String envelope = """
                {"id":"22222222-2222-2222-2222-222222222222","type":"refresh.completed","version":1,\
                "occurredAt":"2026-09-24T09:31:00Z","source":"cairn-api",\
                "data":{"assetClasses":[],"refreshed":0,"failed":0,"trigger":"MANUAL"}}\
                """;
        kafkaTemplate.send("cairn.portfolio", envelope);

        awaitUntil(() -> valuations.count() == 1, "no valuation point recorded");
        assertThat(valuations.findAll()).hasSize(1);
    }

    private void awaitTheCairnValuationContainerToHaveAssignedPartitions() {
        MessageListenerContainer container = registry.getListenerContainer("cairn-valuation");
        awaitUntil(
                () -> container.getAssignedPartitions() != null
                        && !container.getAssignedPartitions().isEmpty(),
                "cairn-valuation never got a partition assignment");
    }

    private static void awaitUntil(BooleanSupplier condition, String failure) {
        long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(failure + " within " + POLL_TIMEOUT);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }
}
