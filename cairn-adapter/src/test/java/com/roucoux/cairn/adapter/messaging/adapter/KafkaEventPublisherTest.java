package com.roucoux.cairn.adapter.messaging.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.events.PriceUpdatedEvent;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import com.roucoux.cairn.events.Topics;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaEventPublisherTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setAdditive(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAsyncSendFailureNeverReachesTheCaller() {
        KafkaTemplate<String, SpecificRecord> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), any(), any(SpecificRecord.class)))
                .thenReturn(CompletableFuture.<SendResult<String, SpecificRecord>>failedFuture(
                        new TimeoutException("broker down")));
        KafkaEventPublisher publisher = new KafkaEventPublisher(template, "cairn-api");

        assertThatCode(() ->
                        publisher.publish(new RefreshCompleted(Set.of(AssetClass.ETF), 1, 0, RefreshTrigger.MANUAL)))
                .doesNotThrowAnyException();

        assertThat(appender.list).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSynchronousSendFailureIsLoggedOnceAtWarnAndSwallowed() {
        KafkaTemplate<String, SpecificRecord> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), any(), any(SpecificRecord.class)))
                .thenThrow(new IllegalStateException("registry down"));
        KafkaEventPublisher publisher = new KafkaEventPublisher(template, "cairn-api");

        assertThatCode(() ->
                        publisher.publish(new RefreshCompleted(Set.of(AssetClass.ETF), 1, 0, RefreshTrigger.MANUAL)))
                .doesNotThrowAnyException();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPriceUpdateGoesToThePricesTopicKeyedByInstrumentWithFreshMetadata() {
        KafkaTemplate<String, SpecificRecord> template = mock(KafkaTemplate.class);
        KafkaEventPublisher publisher = new KafkaEventPublisher(template, "cairn-api");
        UUID instrument = UUID.randomUUID();

        publisher.publish(new PriceUpdated(new Quote(
                instrument,
                LocalDate.of(2026, 9, 23),
                new BigDecimal("2396.17"),
                "EUR",
                PriceSource.AMUNDI,
                Instant.parse("2026-09-23T13:45:02Z"))));

        ArgumentCaptor<SpecificRecord> value = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(template).send(eq(Topics.PRICES), eq(instrument.toString()), value.capture());
        PriceUpdatedEvent record = (PriceUpdatedEvent) value.getValue();
        assertThat(record.getMetadata().getSource()).isEqualTo("cairn-api");
        assertThat(record.getMetadata().getId()).isNotNull();
        assertThat(record.getMetadata().getOccurredAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRefreshGoesToThePortfolioTopicWithNoKey() {
        KafkaTemplate<String, SpecificRecord> template = mock(KafkaTemplate.class);
        KafkaEventPublisher publisher = new KafkaEventPublisher(template, "cairn-batch");

        publisher.publish(new RefreshCompleted(Set.of(AssetClass.CRYPTO), 3, 2, RefreshTrigger.BATCH));

        ArgumentCaptor<SpecificRecord> value = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(template).send(eq(Topics.PORTFOLIO), isNull(), value.capture());
        RefreshCompletedEvent record = (RefreshCompletedEvent) value.getValue();
        assertThat(record.getRefreshed()).isEqualTo(3);
        assertThat(record.getFailed()).isEqualTo(2);
        assertThat(record.getMetadata().getSource()).isEqualTo("cairn-batch");
    }
}
