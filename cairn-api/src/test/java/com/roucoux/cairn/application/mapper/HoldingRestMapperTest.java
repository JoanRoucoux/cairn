package com.roucoux.cairn.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.ValuedHolding;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldingRestMapperTest {

    private final HoldingRestMapper mapper = new HoldingRestMapper(Clock.systemUTC());
    private final Account savings = new Account(UUID.randomUUID(), "Fortuneo", AccountType.SAVINGS, "Fortuneo");

    @Test
    void marksTheAccountEuroCashBalance() {
        Instrument euros = new Instrument(
                UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);

        assertThat(mapper.toResponse(valued(euros)).getAccountCash()).isTrue();
    }

    @Test
    void doesNotMarkASavingsBooklet() {
        Instrument livretA = new Instrument(
                UUID.randomUUID(), "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);

        assertThat(mapper.toResponse(valued(livretA)).getAccountCash()).isFalse();
    }

    @Test
    void doesNotMarkAHoldingItCouldNotValue() {
        Holding holding =
                new Holding(UUID.randomUUID(), savings.id(), UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE);

        assertThat(mapper.toResponse(holding).getAccountCash()).isFalse();
    }

    private ValuedHolding valued(Instrument instrument) {
        Holding holding =
                new Holding(UUID.randomUUID(), savings.id(), instrument.id(), new BigDecimal("1500"), BigDecimal.ONE);
        return new ValuedHolding(holding, instrument, savings, Optional.empty(), Optional.empty());
    }
}
