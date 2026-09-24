package com.roucoux.cairn.kafka.schedule;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        Heartbeat heartbeat = new Heartbeat(RestClient.builder(), wireMock.baseUrl() + "/push", "intraday");

        heartbeat.ping();

        wireMock.verify(1, getRequestedFor(urlEqualTo("/push")));
    }

    @Test
    void makesNoHttpCallWhenNoUrlIsConfigured() {
        RestClient restClient = mock(RestClient.class);
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.requestFactory(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);
        Heartbeat heartbeat = new Heartbeat(builder, "", "intraday");

        heartbeat.ping();

        verifyNoInteractions(restClient);
    }

    @Test
    void aFailedPingIsLoggedNeverPropagated() {
        wireMock.stubFor(get(urlEqualTo("/push")).willReturn(serverError()));
        Heartbeat heartbeat = new Heartbeat(RestClient.builder(), wireMock.baseUrl() + "/push", "intraday");

        assertThatCode(heartbeat::ping).doesNotThrowAnyException();
    }

    @Test
    void aFailedSummaryPingLogsTheSummaryMonitorName() {
        wireMock.stubFor(get(urlEqualTo("/push")).willReturn(serverError()));
        Heartbeat heartbeat = new Heartbeat(RestClient.builder(), wireMock.baseUrl() + "/push", "summary");
        Logger logger = (Logger) LoggerFactory.getLogger(Heartbeat.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            heartbeat.ping();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo("summary heartbeat ping failed");
    }
}
