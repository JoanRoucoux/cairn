package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;

public record NewInstrument(
        String name,
        AssetClass assetClass,
        PriceSource priceSource,
        String sourceRef,
        String isin,
        String symbol,
        BigDecimal price) {}
