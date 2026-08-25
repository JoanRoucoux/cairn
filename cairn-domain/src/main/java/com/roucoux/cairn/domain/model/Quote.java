package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** The current price of an instrument, as seen by the market data provider. */
public record Quote(String isin, BigDecimal price) {

    public Quote {
        Objects.requireNonNull(isin, "isin");
        Objects.requireNonNull(price, "price");
    }
}
