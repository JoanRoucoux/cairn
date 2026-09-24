package com.roucoux.cairn.kafka.schedule;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HeartbeatTest {

    private WireMockServer wireMock;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    @Test
    void pingsTheConfiguredUrl() {
        wireMock.stubFor(get(urlEqualTo("/push")).willReturn(ok()));
        Heartbeat heartbeat = new Heartbeat(RestClient.builder(), wireMock.baseUrl() + "/push");

        heartbeat.ping();

        wireMock.verify(1, getRequestedFor(urlEqualTo("/push")));
    }

    @Test
    void callsNothingWhenNoUrlIsConfigured() {
        Heartbeat heartbeat = new Heartbeat(RestClient.builder(), "");

        heartbeat.ping();

        assertThatCode(() -> wireMock.verify(0, getRequestedFor(urlEqualTo("/push"))))
                .doesNotThrowAnyException();
    }

    @Test
    void aFailedPingIsLoggedNeverPropagated() {
        wireMock.stubFor(get(urlEqualTo("/push")).willReturn(serverError()));
        Heartbeat heartbeat = new Heartbeat(RestClient.builder(), wireMock.baseUrl() + "/push");

        assertThatCode(heartbeat::ping).doesNotThrowAnyException();
    }
}
