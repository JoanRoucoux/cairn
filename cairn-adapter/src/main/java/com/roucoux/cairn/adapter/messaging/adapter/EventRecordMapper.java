package com.roucoux.cairn.adapter.messaging.adapter;

import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.events.AssetClass;
import com.roucoux.cairn.events.EventMetadata;
import com.roucoux.cairn.events.PriceSource;
import com.roucoux.cairn.events.PriceUpdatedEvent;
import com.roucoux.cairn.events.RefreshCompletedEvent;
import com.roucoux.cairn.events.RefreshTrigger;
import java.math.RoundingMode;
import java.util.List;

final class EventRecordMapper {

    private static final int PRICE_SCALE = 12;

    private EventRecordMapper() {}

    static PriceUpdatedEvent toRecord(PriceUpdated event, EventMetadata metadata) {
        Quote quote = event.quote();
        return PriceUpdatedEvent.newBuilder()
                .setMetadata(metadata)
                .setInstrumentId(quote.instrumentId())
                .setAsOf(quote.asOf())
                .setPrice(quote.price().setScale(PRICE_SCALE, RoundingMode.HALF_UP))
                .setCurrency(quote.currency())
                .setPriceSource(PriceSource.valueOf(quote.source().name()))
                .build();
    }

    static RefreshCompletedEvent toRecord(RefreshCompleted event, EventMetadata metadata) {
        List<AssetClass> assetClasses = event.assetClasses().stream()
                .map(assetClass -> AssetClass.valueOf(assetClass.name()))
                .sorted()
                .toList();
        return RefreshCompletedEvent.newBuilder()
                .setMetadata(metadata)
                .setAssetClasses(assetClasses)
                .setRefreshed(event.refreshed())
                .setFailed(event.failed())
                .setTrigger(RefreshTrigger.valueOf(event.trigger().name()))
                .build();
    }
}
