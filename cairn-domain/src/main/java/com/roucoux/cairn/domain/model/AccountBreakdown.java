package com.roucoux.cairn.domain.model;

import java.util.List;
import java.util.Objects;

public record AccountBreakdown(Money total, List<AccountAllocation> items, int unvaluedCount, int nonEurCount) {
    public AccountBreakdown {
        Objects.requireNonNull(total, "total");
        items = List.copyOf(items);
    }
}
