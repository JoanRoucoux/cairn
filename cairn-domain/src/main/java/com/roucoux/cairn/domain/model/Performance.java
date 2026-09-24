package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Performance(
        PerformanceRange range,
        LocalDate from,
        LocalDate to,
        boolean reconstructed,
        Optional<Instant> lastPriceAt,
        Money total,
        Money change,
        Optional<BigDecimal> changeRatio,
        List<EnvelopePerformance> byEnvelope) {

    public Performance {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(lastPriceAt, "lastPriceAt");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(changeRatio, "changeRatio");
        Objects.requireNonNull(byEnvelope, "byEnvelope");
    }
}
