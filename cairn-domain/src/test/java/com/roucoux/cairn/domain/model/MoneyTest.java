package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsTwoAmountsOfTheSameCurrency() {
        Money sum = Money.eur(new BigDecimal("182.36")).plus(Money.eur(new BigDecimal("244.20")));

        assertThat(sum.amount()).isEqualByComparingTo("426.56");
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        assertThatThrownBy(() -> Money.eur(BigDecimal.ONE).plus(new Money(BigDecimal.ONE, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsFullPrecisionInsteadOfRoundingOnConstruction() {
        Money tiny = Money.eur(new BigDecimal("0.000057520000"));

        assertThat(tiny.amount()).isEqualByComparingTo("0.00005752");
    }

    @Test
    void subtractsTwoAmountsOfTheSameCurrency() {
        Money difference = Money.eur(new BigDecimal("244.20")).minus(Money.eur(new BigDecimal("182.36")));

        assertThat(difference.amount()).isEqualByComparingTo("61.84");
    }

    @Test
    void refusesToSubtractDifferentCurrencies() {
        assertThatThrownBy(() -> Money.eur(BigDecimal.ONE).minus(new Money(BigDecimal.ONE, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroEurIsZero() {
        assertThat(Money.zeroEur().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
