package com.roucoux.cairn.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class QuoteId implements Serializable {

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    protected QuoteId() {}

    QuoteId(UUID instrumentId, LocalDate asOf) {
        this.instrumentId = instrumentId;
        this.asOf = asOf;
    }

    UUID instrumentId() {
        return instrumentId;
    }

    LocalDate asOf() {
        return asOf;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuoteId quoteId)) {
            return false;
        }
        return Objects.equals(instrumentId, quoteId.instrumentId) && Objects.equals(asOf, quoteId.asOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrumentId, asOf);
    }
}
