package com.roucoux.cairn.domain.model;

import java.util.Objects;

public record Preferences(Language language, Theme theme) {

    public Preferences {
        Objects.requireNonNull(language);
        Objects.requireNonNull(theme);
    }
}
