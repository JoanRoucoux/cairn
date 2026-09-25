package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.generated.model.AssetClass;
import com.roucoux.cairn.generated.model.InstrumentCandidateResponse;
import com.roucoux.cairn.generated.model.InstrumentDetailResponse;
import com.roucoux.cairn.generated.model.InstrumentResponse;
import com.roucoux.cairn.generated.model.PriceSource;
import org.springframework.stereotype.Component;

@Component
public class InstrumentRestMapper {

    public InstrumentResponse toResponse(Instrument instrument) {
        InstrumentResponse response = new InstrumentResponse();
        response.setId(instrument.id());
        response.setName(instrument.name());
        response.setIsin(instrument.isin());
        response.setCurrency(instrument.currency());
        response.setAssetClass(AssetClass.valueOf(instrument.assetClass().name()));
        response.setPriceSource(PriceSource.valueOf(instrument.priceSource().name()));
        response.setSourceRef(instrument.sourceRef());
        return response;
    }

    public InstrumentDetailResponse toDetailResponse(Instrument instrument, int holdingCount) {
        InstrumentDetailResponse response = new InstrumentDetailResponse();
        response.setId(instrument.id());
        response.setName(instrument.name());
        response.setIsin(instrument.isin());
        response.setCurrency(instrument.currency());
        response.setAssetClass(AssetClass.valueOf(instrument.assetClass().name()));
        response.setPriceSource(PriceSource.valueOf(instrument.priceSource().name()));
        response.setSourceRef(instrument.sourceRef());
        response.setDescription(instrument.description());
        instrument.externalUrl().ifPresent(response::setExternalUrl);
        response.setHoldingCount(holdingCount);
        return response;
    }

    public InstrumentCandidateResponse toCandidateResponse(InstrumentCandidate candidate) {
        InstrumentCandidateResponse response = new InstrumentCandidateResponse();
        response.setName(candidate.name());
        response.setSource(PriceSource.valueOf(candidate.source().name()));
        response.setSourceRef(candidate.sourceRef());
        response.setAssetClass(AssetClass.valueOf(candidate.assetClass().name()));
        response.setProbePrice(candidate.probePrice());
        return response;
    }
}
