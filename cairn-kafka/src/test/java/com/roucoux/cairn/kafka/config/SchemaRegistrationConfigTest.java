package com.roucoux.cairn.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.roucoux.cairn.events.PriceUpdatedEvent;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SchemaRegistrationConfigTest {

    private static final String BASE = "http://registry.test/apis/registry/v3";
    private static final String EVENTS =
            BASE + "/groups/com.roucoux.cairn.events/artifacts?ifExists=FIND_OR_CREATE_VERSION";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final SchemaRegistrationConfig config = new SchemaRegistrationConfig();

    @Test
    void registersBothEventSchemasByContentUnderTheEventsGroup() {
        expect("PriceUpdatedEvent", PriceUpdatedEvent.getClassSchema().toString());
        expect("RefreshCompletedEvent", RefreshCompletedEvent.getClassSchema().toString());

        config.schemaRegistrar(builder.build()).registerAll();

        server.verify();
    }

    @Test
    void theInitialisingSingletonRegistersOnStartup() {
        expect("PriceUpdatedEvent", PriceUpdatedEvent.getClassSchema().toString());
        expect("RefreshCompletedEvent", RefreshCompletedEvent.getClassSchema().toString());
        SmartInitializingSingleton singleton = config.schemaRegistration(config.schemaRegistrar(builder.build()));

        singleton.afterSingletonsInstantiated();

        server.verify();
    }

    @Test
    void aRefusalPropagatesAndNamesTheArtifact() {
        server.expect(requestTo(EVENTS)).andRespond(withServerError());
        SchemaRegistrar registrar = config.schemaRegistrar(builder.build());

        assertThatThrownBy(registrar::registerAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PriceUpdatedEvent");
    }

    @Test
    void buildsTheRegistryClientFromTheConfiguredUrl() {
        assertThat(config.registryRestClient(RestClient.builder(), BASE)).isNotNull();
    }

    private void expect(String artifactId, String schema) {
        server.expect(requestTo(EVENTS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(bodyDescribes(artifactId, schema))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private static RequestMatcher bodyDescribes(String artifactId, String schema) {
        return request -> {
            JsonNode body = JSON.readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertThat(body.path("artifactId").asString()).isEqualTo(artifactId);
            assertThat(body.path("artifactType").asString()).isEqualTo("AVRO");
            assertThat(body.path("firstVersion").path("content").path("content").asString())
                    .isEqualTo(schema);
        };
    }
}
