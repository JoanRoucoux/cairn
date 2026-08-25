package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Allocation(String label, Money value, BigDecimal share) {
    public Allocation {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(share, "share");
    }
}
