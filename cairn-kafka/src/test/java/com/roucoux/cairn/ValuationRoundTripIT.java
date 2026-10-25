package com.roucoux.cairn;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.IntradayValuationJpaRepository;
import com.roucoux.cairn.events.Topics;
import com.roucoux.cairn.kafka.RefreshCompletedEvents;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ValuationRoundTripIT {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(15);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Container
    static GenericContainer<?> apicurio = new GenericContainer<>("apicurio/apicurio-registry:3.3.3")
            .withExposedPorts(8080, 9000)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(120)));

    @DynamicPropertySource
    static void registerRegistryUrl(DynamicPropertyRegistry properties) {
        properties.add(
                "APICURIO_REGISTRY_URL",
                () -> "http://" + apicurio.getHost() + ":" + apicurio.getMappedPort(8080) + "/apis/registry/v3");
    }

    @Autowired
    private KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private IntradayValuationJpaRepository valuations;

    @Autowired
    private Tracer tracer;

    @Autowired
    private ObservationRegistry observationRegistry;

    @BeforeEach
    void emptyTheValuations() {
        valuations.deleteAll();
    }

    @Test
    void aRefreshCompletedEventIsTurnedIntoARecordedValuation(CapturedOutput output) {
        awaitTheCairnValuationContainerToHaveAssignedPartitions();

        String traceId = Observation.createNotStarted("test-publish", observationRegistry)
                .observe(() -> {
                    kafkaTemplate.send(Topics.PORTFOLIO, RefreshCompletedEvents.sample());
                    return tracer.currentSpan().context().traceId();
                });

        awaitUntil(() -> valuations.count() == 1, "no valuation point recorded");
        awaitUntil(
                () -> output.getOut()
                        .lines()
                        .anyMatch(line ->
                                line.contains("use case RecordValuation.record succeeded") && line.contains(traceId)),
                "no RecordValuation line carrying the publishing trace id");
    }

    @Test
    void aPoisonRecordIsSkippedAndTheNextEventIsRecorded() {
        awaitTheCairnValuationContainerToHaveAssignedPartitions();

        DefaultKafkaProducerFactory<String, byte[]> factory = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class));
        try {
            new KafkaTemplate<>(factory)
                    .send(Topics.PORTFOLIO, "not avro".getBytes())
                    .join();
        } finally {
            factory.destroy();
        }
        kafkaTemplate.send(Topics.PORTFOLIO, RefreshCompletedEvents.sample());

        awaitUntil(() -> valuations.count() == 1, "the valid event after the poison record was not recorded");
        assertThat(valuations.count()).isEqualTo(1);
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
