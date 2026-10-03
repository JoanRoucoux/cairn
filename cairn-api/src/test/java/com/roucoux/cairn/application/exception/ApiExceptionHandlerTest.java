package com.roucoux.cairn.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ApiExceptionHandlerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

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
    void logsATechnicalFailureWithItsStack() {
        handler.handleTechnical(new MarketDataUnavailableException("yahoo down"));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void doesNotLogABusinessRefusal() {
        handler.handleNotFound(new NotFoundException("account", UUID.randomUUID()));

        assertThat(appender.list).isEmpty();
    }
}
