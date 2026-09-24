package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record EnvelopePerformance(
        AccountType accountType, Money value, BigDecimal share, Money change, Optional<BigDecimal> changeRatio) {
    public EnvelopePerformance {
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(share, "share");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(changeRatio, "changeRatio");
    }
}
