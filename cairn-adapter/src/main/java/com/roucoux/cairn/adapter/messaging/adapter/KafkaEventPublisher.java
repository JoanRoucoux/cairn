package com.roucoux.cairn.adapter.messaging.adapter;

import com.roucoux.cairn.adapter.messaging.properties.KafkaMessagingProperties;
import com.roucoux.cairn.domain.model.event.DomainEvent;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Outbound adapter: turns a domain event into the envelope on the wire, never failing the caller. */
public class KafkaEventPublisher implements PublishEventPort {

    private static final String SOURCE = "cairn";
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final KafkaMessagingProperties properties;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper, KafkaMessagingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public void publish(DomainEvent event) {
        Publication publication = publicationFor(event);
        try {
            String payload = jsonMapper.writeValueAsString(publication.envelope());
            kafkaTemplate
                    .send(publication.topic(), publication.key(), payload)
                    .whenComplete((result, failure) ->
                            logIfFailed(publication.envelope().type(), failure));
        } catch (RuntimeException failure) {
            log.warn("failed to publish a {} event", publication.envelope().type(), failure);
        }
    }

    private Publication publicationFor(DomainEvent event) {
        return switch (event) {
            case PriceUpdated priceUpdated ->
                new Publication(
                        properties.pricesTopic(),
                        priceUpdated.quote().instrumentId().toString(),
                        envelopeOf("price.updated", priceUpdated.quote()));
            case RefreshCompleted refreshCompleted ->
                new Publication(
                        properties.portfolioTopic(),
                        refreshCompleted.trigger().name(),
                        envelopeOf("refresh.completed", refreshCompleted));
        };
    }

    private EventEnvelope envelopeOf(String type, Object data) {
        return new EventEnvelope(UUID.randomUUID().toString(), type, 1, Instant.now(), SOURCE, data);
    }

    private void logIfFailed(String type, Throwable failure) {
        if (failure != null) {
            log.warn("failed to publish a {} event", type, failure);
        }
    }

    private record Publication(String topic, String key, EventEnvelope envelope) {}
}
