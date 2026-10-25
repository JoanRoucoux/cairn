package com.roucoux.cairn.adapter.messaging.adapter;

import com.roucoux.cairn.domain.model.event.DomainEvent;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import com.roucoux.cairn.events.EventMetadata;
import com.roucoux.cairn.events.Topics;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaEventPublisher implements PublishEventPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final String source;

    public KafkaEventPublisher(KafkaTemplate<String, SpecificRecord> kafkaTemplate, String source) {
        this.kafkaTemplate = kafkaTemplate;
        this.source = source;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            EventMetadata metadata = EventMetadata.newBuilder()
                    .setId(UUID.randomUUID())
                    .setOccurredAt(Instant.now())
                    .setSource(source)
                    .build();
            switch (event) {
                case PriceUpdated priceUpdated ->
                    kafkaTemplate.send(
                            Topics.PRICES,
                            priceUpdated.quote().instrumentId().toString(),
                            EventRecordMapper.toRecord(priceUpdated, metadata));
                case RefreshCompleted refreshCompleted ->
                    kafkaTemplate.send(Topics.PORTFOLIO, null, EventRecordMapper.toRecord(refreshCompleted, metadata));
            }
        } catch (RuntimeException failure) {
            log.warn("failed to publish a {} event", event.getClass().getSimpleName(), failure);
        }
    }
}
