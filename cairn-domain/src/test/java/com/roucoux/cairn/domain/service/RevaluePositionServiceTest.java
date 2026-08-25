package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.out.MarketDataPort;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevaluePositionServiceTest {

    private static final String ISIN = "US0378331005";

    @Mock
    private MarketDataPort marketDataPort;

    @Test
    void revalueKeepsTheHoldingAndAppliesTheCurrentPrice() {
        Position position = Position.open(ISIN, new BigDecimal("10"), new BigDecimal("100.00"));
        when(marketDataPort.currentPrice(ISIN)).thenReturn(Optional.of(new BigDecimal("123.45")));

        Position revalued = new RevaluePositionService(marketDataPort).revalue(position);

        assertThat(revalued.id()).isEqualTo(position.id());
        assertThat(revalued.quantity()).isEqualTo(position.quantity());
        assertThat(revalued.createdAt()).isEqualTo(position.createdAt());
        assertThat(revalued.price()).isEqualTo(new BigDecimal("123.45"));
    }

    @Test
    void revalueRejectsAnInstrumentTheProviderNoLongerKnows() {
        Position position = Position.open(ISIN, BigDecimal.ONE, BigDecimal.TEN);
        when(marketDataPort.currentPrice(ISIN)).thenReturn(Optional.empty());

        RevaluePositionService service = new RevaluePositionService(marketDataPort);

        assertThatExceptionOfType(UnknownInstrumentException.class)
                .isThrownBy(() -> service.revalue(position))
                .withMessageContaining(ISIN);
    }
}
