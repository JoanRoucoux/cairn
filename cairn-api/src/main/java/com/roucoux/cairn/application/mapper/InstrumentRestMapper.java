package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.generated.model.InstrumentCandidateResponse;
import com.roucoux.cairn.generated.model.InstrumentDetailResponse;
import com.roucoux.cairn.generated.model.InstrumentResponse;
import org.springframework.stereotype.Component;

/** Maps a plain {@link Instrument} or {@link InstrumentCandidate} to the generated DTOs. */
@Component
public class InstrumentRestMapper {

    public InstrumentResponse toResponse(Instrument instrument) {
        InstrumentResponse response = new InstrumentResponse();
        response.setId(instrument.id());
        response.setName(instrument.name());
        response.setIsin(instrument.isin());
        response.setCurrency(instrument.currency());
        response.setAssetClass(InstrumentResponse.AssetClassEnum.valueOf(
                instrument.assetClass().name()));
        response.setPriceSource(InstrumentResponse.PriceSourceEnum.valueOf(
                instrument.priceSource().name()));
        response.setSourceRef(instrument.sourceRef());
        return response;
    }

    public InstrumentDetailResponse toDetailResponse(Instrument instrument) {
        InstrumentDetailResponse response = new InstrumentDetailResponse();
        response.setId(instrument.id());
        response.setName(instrument.name());
        response.setIsin(instrument.isin());
        response.setCurrency(instrument.currency());
        response.setAssetClass(InstrumentDetailResponse.AssetClassEnum.valueOf(
                instrument.assetClass().name()));
        response.setPriceSource(InstrumentDetailResponse.PriceSourceEnum.valueOf(
                instrument.priceSource().name()));
        response.setSourceRef(instrument.sourceRef());
        response.setDescription(instrument.description());
        instrument.externalUrl().ifPresent(response::setExternalUrl);
        return response;
    }

    public InstrumentCandidateResponse toCandidateResponse(InstrumentCandidate candidate) {
        InstrumentCandidateResponse response = new InstrumentCandidateResponse();
        response.setName(candidate.name());
        response.setSource(InstrumentCandidateResponse.SourceEnum.valueOf(
                candidate.source().name()));
        response.setSourceRef(candidate.sourceRef());
        response.setAssetClass(InstrumentCandidateResponse.AssetClassEnum.valueOf(
                candidate.assetClass().name()));
        response.setProbePrice(candidate.probePrice());
        return response;
    }
}
