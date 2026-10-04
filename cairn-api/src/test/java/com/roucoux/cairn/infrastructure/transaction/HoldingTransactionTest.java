package com.roucoux.cairn.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.NewInstrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class HoldingTransactionTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID INSTRUMENT_ID = UUID.randomUUID();

    private final ManageHoldingUseCase manageHolding = mock(ManageHoldingUseCase.class);
    private final HoldingTransaction transaction = new HoldingTransaction(manageHolding);

    @Test
    void createsOnAnExistingInstrumentThroughTheUseCase() {
        Holding holding =
                new Holding(UUID.randomUUID(), ACCOUNT_ID, INSTRUMENT_ID, BigDecimal.ONE, null, Instant.EPOCH);
        when(manageHolding.create(ACCOUNT_ID, INSTRUMENT_ID, BigDecimal.ONE, null))
                .thenReturn(holding);

        assertThat(transaction.create(ACCOUNT_ID, INSTRUMENT_ID, BigDecimal.ONE, null))
                .isSameAs(holding);
    }

    @Test
    void createsOnANewInstrumentThroughTheUseCase() {
        NewInstrument instrument = new NewInstrument(
                "Woodgrove Notes", AssetClass.BOND, PriceSource.MANUAL, null, null, null, BigDecimal.TEN);
        Holding holding =
                new Holding(UUID.randomUUID(), ACCOUNT_ID, INSTRUMENT_ID, BigDecimal.ONE, null, Instant.EPOCH);
        when(manageHolding.createWithNewInstrument(ACCOUNT_ID, instrument, BigDecimal.ONE, null))
                .thenReturn(holding);

        assertThat(transaction.createWithNewInstrument(ACCOUNT_ID, instrument, BigDecimal.ONE, null))
                .isSameAs(holding);
    }

    @Test
    void everyWriteRunsInsideATransaction() {
        assertThat(Arrays.stream(HoldingTransaction.class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .allMatch(method -> method.isAnnotationPresent(Transactional.class)))
                .isTrue();
    }
}
