package com.roucoux.cairn.domain.model;

import java.util.List;
import java.util.Objects;

public record AssetClassBreakdown(Money total, List<AssetClassAllocation> items, int unvaluedCount, int nonEurCount) {
    public AssetClassBreakdown {
        Objects.requireNonNull(total, "total");
        items = List.copyOf(items);
    }
}
