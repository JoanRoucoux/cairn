package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;

public record InstrumentCandidate(
        String name,
        PriceSource source,
        String sourceRef,
        AssetClass assetClass,
        String exchange,
        BigDecimal probePrice) {}
