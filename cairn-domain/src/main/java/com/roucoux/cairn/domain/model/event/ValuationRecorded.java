package com.roucoux.cairn.domain.model.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record ValuationRecorded(Instant at, BigDecimal totalEur, BigDecimal dayChangeEur) implements DomainEvent {

    public ValuationRecorded {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(totalEur, "totalEur");
        Objects.requireNonNull(dayChangeEur, "dayChangeEur");
    }
}
