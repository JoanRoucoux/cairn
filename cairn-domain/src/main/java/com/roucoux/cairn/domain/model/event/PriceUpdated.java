package com.roucoux.cairn.domain.model.event;

import com.roucoux.cairn.domain.model.Quote;
import java.util.Objects;

public record PriceUpdated(Quote quote) implements DomainEvent {
    public PriceUpdated {
        Objects.requireNonNull(quote, "quote");
    }
}
