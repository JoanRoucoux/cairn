package com.roucoux.cairn.domain.model;

import java.util.List;
import java.util.Map;

public record ImportPlan(List<ImportRow> rows, Map<String, InstrumentCandidate> candidates) {

    public ImportPlan {
        rows = List.copyOf(rows);
        candidates = Map.copyOf(candidates);
    }
}
