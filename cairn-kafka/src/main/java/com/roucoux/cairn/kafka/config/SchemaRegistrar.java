package com.roucoux.cairn.kafka.config;

import com.roucoux.cairn.events.PriceUpdatedEvent;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import java.util.Map;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class SchemaRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SchemaRegistrar.class);
    private static final String GROUP = "com.roucoux.cairn.events";

    private final RestClient registry;

    SchemaRegistrar(RestClient registry) {
        this.registry = registry;
    }

    public void registerAll() {
        register(PriceUpdatedEvent.getClassSchema());
        register(RefreshCompletedEvent.getClassSchema());
    }

    private void register(Schema schema) {
        String artifactId = schema.getName();
        Map<String, Object> body = Map.of(
                "artifactId",
                artifactId,
                "artifactType",
                "AVRO",
                "firstVersion",
                Map.of("content", Map.of("content", schema.toString(), "contentType", "application/json")));
        try {
            registry.post()
                    .uri("/groups/{group}/artifacts?ifExists=FIND_OR_CREATE_VERSION", GROUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException refused) {
            log.error("schema registration refused for artifact {}", artifactId);
            throw new IllegalStateException("schema registration failed for artifact " + artifactId, refused);
        }
    }
}
