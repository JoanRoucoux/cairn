package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.MarketDataPort;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The whole domain tests without Spring: plain JUnit and Mockito against the ports. */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    private static final String ISIN = "US0378331005";

    @Mock
    private MarketDataPort marketDataPort;

    @Test
    void byIsinReturnsThePriceSeenByTheProvider() {
        when(marketDataPort.currentPrice(ISIN)).thenReturn(Optional.of(new BigDecimal("123.45")));

        Quote quote = new QuoteService(marketDataPort).byIsin(ISIN);

        assertThat(quote).isEqualTo(new Quote(ISIN, new BigDecimal("123.45")));
    }

    @Test
    void byIsinRejectsAnUnknownInstrument() {
        when(marketDataPort.currentPrice(ISIN)).thenReturn(Optional.empty());

        QuoteService service = new QuoteService(marketDataPort);

        assertThatExceptionOfType(UnknownInstrumentException.class)
                .isThrownBy(() -> service.byIsin(ISIN))
                .withMessageContaining(ISIN);
    }
}
