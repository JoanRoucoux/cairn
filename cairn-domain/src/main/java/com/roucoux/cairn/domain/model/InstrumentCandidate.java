package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;

/** A possible match for an ISIN, offered by one price source during resolution. */
public record InstrumentCandidate(
        String name, PriceSource source, String sourceRef, AssetClass assetClass, BigDecimal probePrice) {}
