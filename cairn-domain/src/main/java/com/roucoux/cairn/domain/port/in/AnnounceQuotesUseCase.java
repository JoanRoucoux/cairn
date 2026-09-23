package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import java.util.List;
import java.util.Set;

/** Inbound port: turns quote writes and finished refreshes into the domain events that announce them. */
public interface AnnounceQuotesUseCase {

    void quotesSaved(List<Quote> quotes);

    void refreshCompleted(Set<AssetClass> assetClasses, int refreshed, int failed, RefreshTrigger trigger);
}
