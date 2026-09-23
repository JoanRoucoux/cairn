package com.roucoux.cairn.domain.model.event;

import com.roucoux.cairn.domain.model.AssetClass;
import java.util.Objects;
import java.util.Set;

public record RefreshCompleted(Set<AssetClass> assetClasses, int refreshed, int failed, RefreshTrigger trigger)
        implements DomainEvent {
    public RefreshCompleted {
        assetClasses = Set.copyOf(assetClasses);
        Objects.requireNonNull(trigger, "trigger");
    }
}
