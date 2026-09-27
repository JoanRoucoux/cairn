package com.roucoux.cairn.domain.model;

import java.util.Objects;

public record CashOnlyAccount(String name, AccountType type, Money value) {

    public CashOnlyAccount {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}
