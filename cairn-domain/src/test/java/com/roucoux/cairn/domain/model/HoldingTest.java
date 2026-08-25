package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldingTest {

    @Test
    void exposesItsCostBasisWhenKnown() {
        Holding holding =
                new Holding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE);

        assertThat(holding.costBasis()).contains(BigDecimal.ONE);
    }

    @Test
    void hasNoCostBasisWhenUnknown() {
        Holding holding = new Holding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, null);

        assertThat(holding.costBasis()).isEmpty();
    }
}
