package com.roucoux.cairn.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record Summary(
        SummaryPeriod period,
        LocalDate first,
        LocalDate last,
        Performance performance,
        List<CashOnlyAccount> cashOnlyAccounts) {

    public Summary {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(last, "last");
        Objects.requireNonNull(performance, "performance");
        cashOnlyAccounts = List.copyOf(cashOnlyAccounts);
    }
}
