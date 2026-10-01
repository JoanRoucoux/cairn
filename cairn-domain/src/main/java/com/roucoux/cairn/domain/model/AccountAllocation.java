package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record AccountAllocation(Account account, Money value, BigDecimal share, int lineCount) {
    public AccountAllocation {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(share, "share");
    }
}
