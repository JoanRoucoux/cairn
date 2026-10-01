package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.AssetClassBreakdown;

public interface GetAssetClassAllocationUseCase {

    AssetClassBreakdown byAssetClass();
}
