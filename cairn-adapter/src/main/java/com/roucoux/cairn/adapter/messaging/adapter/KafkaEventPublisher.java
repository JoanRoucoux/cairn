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
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

/** Outbound adapter: turns a domain event into the envelope on the wire, never failing the caller. */
public class KafkaEventPublisher implements PublishEventPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    /** The envelope's price must never render in scientific notation. Built from scratch rather
     * than derived from Boot's JsonMapper, so every emitter writes the same envelope regardless of
     * its own Jackson settings (cairn-api sets non_null inclusion, batch and worker do not). */
    public static JsonMapper eventMapper() {
        return JsonMapper.builder()
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .build();
    }

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final KafkaMessagingProperties properties;
    private final String source;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate, KafkaMessagingProperties properties, String source) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = eventMapper();
        this.properties = properties;
        this.source = source;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            Publication publication = publicationFor(event);
            String payload = jsonMapper.writeValueAsString(publication.envelope());
            kafkaTemplate.send(publication.topic(), publication.key(), payload);
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

    private record Publication(String topic, String key, EventEnvelope envelope) {}
}
