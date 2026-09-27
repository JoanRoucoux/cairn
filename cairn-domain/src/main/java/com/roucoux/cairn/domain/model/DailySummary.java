package com.roucoux.cairn.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailySummary(LocalDate date, Performance performance, List<CashOnlyAccount> cashOnlyAccounts) {

    public DailySummary {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(performance, "performance");
        cashOnlyAccounts = List.copyOf(cashOnlyAccounts);
    }
}
