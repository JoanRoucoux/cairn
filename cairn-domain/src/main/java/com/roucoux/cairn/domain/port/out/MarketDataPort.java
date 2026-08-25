package com.roucoux.cairn.domain.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/** Outbound port: read the current market price of an instrument (backed by an external API). */
public interface MarketDataPort {

    Optional<BigDecimal> currentPrice(String isin);
}
