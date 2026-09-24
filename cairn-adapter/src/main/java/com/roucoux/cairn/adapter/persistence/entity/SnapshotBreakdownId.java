package com.roucoux.cairn.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class SnapshotBreakdownId implements Serializable {

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "dimension", nullable = false, length = 20)
    private String dimension;

    @Column(name = "breakdown_key", nullable = false, length = 80)
    private String breakdownKey;

    protected SnapshotBreakdownId() {}

    SnapshotBreakdownId(LocalDate asOf, String dimension, String breakdownKey) {
        this.asOf = asOf;
        this.dimension = dimension;
        this.breakdownKey = breakdownKey;
    }

    LocalDate asOf() {
        return asOf;
    }

    String dimension() {
        return dimension;
    }

    String breakdownKey() {
        return breakdownKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SnapshotBreakdownId that)) {
            return false;
        }
        return Objects.equals(asOf, that.asOf)
                && Objects.equals(dimension, that.dimension)
                && Objects.equals(breakdownKey, that.breakdownKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asOf, dimension, breakdownKey);
    }
}
