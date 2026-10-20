package com.roucoux.cairn.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record PeriodBounds(LocalDate first, LocalDate last) {

    public PeriodBounds {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(last, "last");
    }

    public LocalDate baseline() {
        return first.minusDays(1);
    }
}
