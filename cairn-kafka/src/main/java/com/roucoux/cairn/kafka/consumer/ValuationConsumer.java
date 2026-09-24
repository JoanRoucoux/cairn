package com.roucoux.cairn.kafka.consumer;

import com.roucoux.cairn.domain.port.in.RecordValuationUseCase;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound adapter: turns every {@code refresh.completed} envelope on {@code cairn.portfolio} into
 * one recorded point of the portfolio's value. Every other envelope on the topic, including the
 * {@code valuation.recorded} ones this same use case publishes, is ignored on purpose.
 */
@Component
public class ValuationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ValuationConsumer.class);
    private static final String REFRESH_COMPLETED = "refresh.completed";

    /**
     * Built the same way as {@code KafkaEventPublisher.eventMapper()} rather than reusing it: the
     * worker depends on {@code cairn-adapter} at runtime only, and this class lives in
     * {@code ..kafka..}, which {@code WorkerArchitectureTest} forbids from depending on
     * {@code ..adapter..}.
     */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RecordValuationUseCase recordValuation;
    private final Clock clock;

    public ValuationConsumer(RecordValuationUseCase recordValuation, Clock clock) {
        this.recordValuation = recordValuation;
        this.clock = clock;
    }

    @KafkaListener(topics = "${app.messaging.kafka.portfolio-topic:cairn.portfolio}", groupId = "cairn-valuation")
    public void onMessage(String payload) {
        String type;
        try {
            JsonNode envelope = JSON_MAPPER.readTree(payload);
            type = envelope.path("type").asString(null);
        } catch (RuntimeException malformed) {
            log.warn("skipping an unreadable envelope on cairn.portfolio", malformed);
            return;
        }

        if (REFRESH_COMPLETED.equals(type)) {
            recordValuation.record(clock.instant());
        }
    }
}
