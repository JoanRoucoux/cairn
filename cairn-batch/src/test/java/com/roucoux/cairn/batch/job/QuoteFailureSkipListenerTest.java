package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class QuoteFailureSkipListenerTest {

    @Test
    void logsTheSkippedInstrumentWithTheStack() {
        Logger logger = (Logger) LoggerFactory.getLogger(QuoteFailureSkipListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Instrument northwind = new Instrument(
                UUID.randomUUID(), "Northwind", null, "EUR", AssetClass.EQUITY, PriceSource.YAHOO, "NWD.PA", null);
        try {
            new QuoteFailureSkipListener(mock(RecordQuoteFailurePort.class), mock(LoadInstrumentsPort.class))
                    .onSkipInProcess(northwind, new IllegalStateException("boom"));

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("Northwind").contains("YAHOO");
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
