package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstrumentCandidate(
        String name,
        PriceSource source,
        String sourceRef,
        AssetClass assetClass,
        String exchange,
        String isin,
        String symbol,
        BigDecimal probePrice,
        String currency,
        LocalDate probeAsOf,
        UUID trackedInstrumentId) {

    public InstrumentCandidate(
            String name,
            PriceSource source,
            String sourceRef,
            AssetClass assetClass,
            String exchange,
            String isin,
            String symbol,
            BigDecimal probePrice,
            String currency) {
        this(name, source, sourceRef, assetClass, exchange, isin, symbol, probePrice, currency, null, null);
    }

    public InstrumentCandidate withTrackedInstrumentId(UUID trackedInstrumentId) {
        return new InstrumentCandidate(
                name,
                source,
                sourceRef,
                assetClass,
                exchange,
                isin,
                symbol,
                probePrice,
                currency,
                probeAsOf,
                trackedInstrumentId);
    }
}
