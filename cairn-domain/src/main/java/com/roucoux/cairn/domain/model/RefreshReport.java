package com.roucoux.cairn.domain.model;

import java.util.List;
import java.util.UUID;

/** Outcome of one synchronous quote refresh, across every instrument considered. */
public record RefreshReport(int refreshed, int skipped, List<Failure> failures) {

    public record Failure(UUID instrumentId, String instrumentName, PriceSource source, String message) {}
}
