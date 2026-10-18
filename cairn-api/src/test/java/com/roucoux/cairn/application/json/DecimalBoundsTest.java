package com.roucoux.cairn.application.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecimalBoundsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0",
                "12",
                "0.000000000001",
                "9999999999999999.999999999999",
                "-26.65",
                "1.500000000000000000",
                "1E+15"
            })
    void acceptsAnyValueTheColumnCanHold(String value) {
        assertThat(DecimalBounds.isWithinBounds(new BigDecimal(value))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.0000000000001",
                "10000000000000000",
                "1e999999999",
                "1e-999999999",
                "-1e999999999",
                "0E-999999999",
                "0E+999999999"
            })
    void refusesAnyValueTheColumnCannotHoldWithoutDoingArithmeticOnIt(String value) {
        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertThat(DecimalBounds.isWithinBounds(new BigDecimal(value)))
                        .isFalse());
    }
}
