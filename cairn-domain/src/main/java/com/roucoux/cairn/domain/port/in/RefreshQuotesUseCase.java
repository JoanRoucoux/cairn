package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import java.util.Set;

/** Inbound port: refresh quotes, dispatching each instrument to the source it declares. */
public interface RefreshQuotesUseCase {

    Quote refresh(Instrument instrument);

    RefreshReport refreshAll(Set<AssetClass> assetClasses);
}
