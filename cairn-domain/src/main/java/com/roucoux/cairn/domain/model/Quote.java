package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Quote(
        UUID instrumentId, LocalDate asOf, BigDecimal price, String currency, PriceSource source, Instant fetchedAt) {

    public Quote {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
    }
}
