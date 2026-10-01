package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record AssetClassAllocation(AssetClass assetClass, Money value, BigDecimal share, int lineCount) {
    public AssetClassAllocation {
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(share, "share");
    }
}
