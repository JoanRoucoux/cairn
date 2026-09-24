package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record IntradayValuation(Instant at, BigDecimal totalEur) {

    public IntradayValuation {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(totalEur, "totalEur");
    }
}
