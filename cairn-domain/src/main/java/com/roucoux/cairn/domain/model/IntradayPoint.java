package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record IntradayPoint(Instant at, BigDecimal totalEur) {

    public IntradayPoint {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(totalEur, "totalEur");
    }
}
