package com.roucoux.cairn.adapter.messaging.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roucoux.cairn.adapter.messaging.properties.KafkaMessagingProperties;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    @SuppressWarnings("unchecked")
    void logsAnAsyncSendFailureWithoutThrowing() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), any(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(
                        new TimeoutException("broker down")));
        KafkaEventPublisher publisher = new KafkaEventPublisher(template, properties(), "cairn-api");

        assertThatCode(() ->
                        publisher.publish(new RefreshCompleted(Set.of(AssetClass.ETF), 1, 0, RefreshTrigger.MANUAL)))
                .doesNotThrowAnyException();

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("RefreshCompleted");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    private static KafkaMessagingProperties properties() {
        return new KafkaMessagingProperties("cairn.prices", "cairn.portfolio");
    }
}
