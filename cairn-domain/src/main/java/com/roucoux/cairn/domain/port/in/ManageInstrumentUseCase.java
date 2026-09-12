package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import java.util.UUID;

public interface ManageInstrumentUseCase {

    Instrument create(
            String name,
            String isin,
            String currency,
            AssetClass assetClass,
            PriceSource priceSource,
            String sourceRef,
            String description);

    Instrument update(
            UUID id,
            String name,
            String isin,
            AssetClass assetClass,
            PriceSource priceSource,
            String sourceRef,
            String description);

    void delete(UUID id);
}
