package com.roucoux.cairn.adapter.messaging.adapter;

import com.roucoux.cairn.adapter.messaging.properties.KafkaMessagingProperties;
import com.roucoux.cairn.domain.model.Quote;
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

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final KafkaMessagingProperties properties;
    private final String source;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            KafkaMessagingProperties properties,
            String source) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.source = source;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            Publication publication = publicationFor(event);
            String payload = jsonMapper.writeValueAsString(publication.envelope());
            kafkaTemplate
                    .send(publication.topic(), publication.key(), payload)
                    .whenComplete((result, failure) ->
                            logIfFailed(publication.envelope().type(), failure));
        } catch (RuntimeException failure) {
            log.warn("failed to publish a {} event", event.getClass().getSimpleName(), failure);
        }
    }

    private Publication publicationFor(DomainEvent event) {
        return switch (event) {
            case PriceUpdated priceUpdated ->
                new Publication(
                        properties.pricesTopic(),
                        priceUpdated.quote().instrumentId().toString(),
                        envelopeOf("price.updated", dataOf(priceUpdated.quote())));
            case RefreshCompleted refreshCompleted ->
                new Publication(properties.portfolioTopic(), null, envelopeOf("refresh.completed", refreshCompleted));
        };
    }

    private static PriceUpdatedData dataOf(Quote quote) {
        return new PriceUpdatedData(
                quote.instrumentId(), quote.asOf(), quote.price(), quote.currency(), quote.source());
    }

    private EventEnvelope envelopeOf(String type, Object data) {
        return new EventEnvelope(UUID.randomUUID().toString(), type, 1, Instant.now(), source, data);
    }

    private void logIfFailed(String type, Throwable failure) {
        if (failure != null) {
            log.warn("failed to publish a {} event", type, failure);
        }
    }

    private record Publication(String topic, String key, EventEnvelope envelope) {}
}
