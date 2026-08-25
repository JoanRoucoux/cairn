package com.roucoux.cairn.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Account(UUID id, String name, AccountType type, String institution) {
    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(institution, "institution");
    }
}
