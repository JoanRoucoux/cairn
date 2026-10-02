package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.InsufficientQuantityException;
import com.roucoux.cairn.domain.exception.business.InvalidTradeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HoldingTest {

    @Test
    void exposesItsCostBasisWhenKnown() {
        Holding holding = new Holding(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE, Instant.EPOCH);

        assertThat(holding.costBasis()).contains(BigDecimal.ONE);
    }

    @Test
    void hasNoCostBasisWhenUnknown() {
        Holding holding = new Holding(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, null, Instant.EPOCH);

        assertThat(holding.costBasis()).isEmpty();
    }

    private static Holding holding(String quantity, String averageCost) {
        return new Holding(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal(quantity),
                averageCost == null ? null : new BigDecimal(averageCost),
                Instant.EPOCH);
    }

    @Test
    void buyingAddsTheQuantityAndWeighsTheAverageCost() {
        Holding bought = holding("500", "24.12").buy(new BigDecimal("40"), new BigDecimal("29.10"));

        assertThat(bought.quantity()).isEqualByComparingTo("540");
        assertThat(bought.averageCost()).isEqualByComparingTo("24.488889");
    }

    @Test
    void theFirstPurchaseSetsAMissingAverageCost() {
        Holding bought = holding("342", null).buy(new BigDecimal("20"), new BigDecimal("51.20"));

        assertThat(bought.quantity()).isEqualByComparingTo("362");
        assertThat(bought.averageCost()).isEqualByComparingTo("51.20");
    }

    @Test
    void buyingKeepsTheQuantityScale() {
        Holding bought = holding("0.00005752", "60000").buy(new BigDecimal("0.00000001"), new BigDecimal("61200"));

        assertThat(bought.quantity()).isEqualByComparingTo("0.00005753");
    }

    @Test
    void buyingKeepsIdentity() {
        Holding original = holding("1", "10");

        Holding bought = original.buy(BigDecimal.ONE, BigDecimal.TEN);

        assertThat(bought.id()).isEqualTo(original.id());
        assertThat(bought.accountId()).isEqualTo(original.accountId());
        assertThat(bought.instrumentId()).isEqualTo(original.instrumentId());
    }

    @Test
    void sellingPartOfTheQuantityKeepsTheAverageCost() {
        Optional<Holding> sold = holding("500", "24.12").sell(new BigDecimal("100"));

        assertThat(sold).hasValueSatisfying(remaining -> {
            assertThat(remaining.quantity()).isEqualByComparingTo("400");
            assertThat(remaining.averageCost()).isEqualByComparingTo("24.12");
        });
    }

    @Test
    void sellingEverythingLeavesNothing() {
        assertThat(holding("500", "24.12").sell(new BigDecimal("500.000000000000")))
                .isEmpty();
    }

    @Test
    void sellingMoreThanHeldIsRefused() {
        assertThatThrownBy(() -> holding("500", "24.12").sell(new BigDecimal("501")))
                .isInstanceOf(InsufficientQuantityException.class)
                .hasMessageContaining("500");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void aTradeNeedsAPositiveQuantity(String quantity) {
        Holding holding = holding("10", "1");

        assertThatThrownBy(() -> holding.buy(new BigDecimal(quantity), BigDecimal.ONE))
                .isInstanceOf(InvalidTradeException.class);
        assertThatThrownBy(() -> holding.sell(new BigDecimal(quantity))).isInstanceOf(InvalidTradeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void aPurchaseNeedsAPositivePrice(String price) {
        assertThatThrownBy(() -> holding("10", "1").buy(BigDecimal.ONE, new BigDecimal(price)))
                .isInstanceOf(InvalidTradeException.class);
    }
}
