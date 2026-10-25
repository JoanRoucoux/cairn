package com.roucoux.cairn.kafka;

import com.roucoux.cairn.events.AssetClass;
import com.roucoux.cairn.events.EventMetadata;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import com.roucoux.cairn.events.RefreshTrigger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RefreshCompletedEvents {

    private RefreshCompletedEvents() {}

    public static RefreshCompletedEvent sample() {
        return RefreshCompletedEvent.newBuilder()
                .setMetadata(EventMetadata.newBuilder()
                        .setId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                        .setOccurredAt(Instant.parse("2026-09-24T09:31:00Z"))
                        .setSource("cairn-api")
                        .build())
                .setAssetClasses(List.of(AssetClass.EQUITY))
                .setRefreshed(2)
                .setFailed(0)
                .setTrigger(RefreshTrigger.MANUAL)
                .build();
    }
}
