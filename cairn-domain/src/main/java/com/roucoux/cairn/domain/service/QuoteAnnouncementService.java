package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.out.PublishEventPort;
import java.util.List;
import java.util.Set;

public class QuoteAnnouncementService implements AnnounceQuotesUseCase {

    private final PublishEventPort publishEvent;

    public QuoteAnnouncementService(PublishEventPort publishEvent) {
        this.publishEvent = publishEvent;
    }

    @Override
    public void quotesSaved(List<Quote> quotes) {
        quotes.forEach(quote -> publishEvent.publish(new PriceUpdated(quote)));
    }

    @Override
    public void refreshCompleted(Set<AssetClass> assetClasses, int refreshed, int failed, RefreshTrigger trigger) {
        publishEvent.publish(new RefreshCompleted(assetClasses, refreshed, failed, trigger));
    }
}
