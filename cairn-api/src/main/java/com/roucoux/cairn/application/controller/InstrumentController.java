package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.InstrumentRestMapper;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.generated.api.InstrumentApi;
import com.roucoux.cairn.generated.model.CreateInstrumentRequest;
import com.roucoux.cairn.generated.model.InstrumentCandidateResponse;
import com.roucoux.cairn.generated.model.InstrumentDetailResponse;
import com.roucoux.cairn.generated.model.InstrumentResponse;
import com.roucoux.cairn.generated.model.ResolveInstrumentRequest;
import com.roucoux.cairn.generated.model.UpdateInstrumentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain. */
@RestController
class InstrumentController implements InstrumentApi {

    private final LoadInstrumentsPort loadInstruments;
    private final SaveInstrumentPort saveInstrument;
    private final ResolveInstrumentUseCase resolveInstrument;
    private final InstrumentRestMapper mapper;

    InstrumentController(
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            ResolveInstrumentUseCase resolveInstrument,
            InstrumentRestMapper mapper) {
        this.loadInstruments = loadInstruments;
        this.saveInstrument = saveInstrument;
        this.resolveInstrument = resolveInstrument;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<InstrumentResponse>> listInstruments() {
        List<InstrumentResponse> instruments =
                loadInstruments.findAll().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(instruments);
    }

    @Override
    public ResponseEntity<InstrumentResponse> createInstrument(CreateInstrumentRequest createInstrumentRequest) {
        Instrument instrument = new Instrument(
                UUID.randomUUID(),
                createInstrumentRequest.getName(),
                createInstrumentRequest.getIsin(),
                createInstrumentRequest.getCurrency(),
                AssetClass.valueOf(createInstrumentRequest.getAssetClass().name()),
                PriceSource.valueOf(createInstrumentRequest.getPriceSource().name()),
                createInstrumentRequest.getSourceRef(),
                createInstrumentRequest.getDescription());
        Instrument saved = saveInstrument.save(instrument);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(saved));
    }

    @Override
    public ResponseEntity<InstrumentDetailResponse> getInstrument(UUID id) {
        Instrument instrument = findInstrumentOrThrow(id);
        return ResponseEntity.ok(mapper.toDetailResponse(instrument));
    }

    @Override
    public ResponseEntity<InstrumentDetailResponse> updateInstrument(
            UUID id, UpdateInstrumentRequest updateInstrumentRequest) {
        Instrument instrument = findInstrumentOrThrow(id);
        Instrument updated = new Instrument(
                instrument.id(),
                instrument.name(),
                instrument.isin(),
                instrument.currency(),
                instrument.assetClass(),
                instrument.priceSource(),
                instrument.sourceRef(),
                updateInstrumentRequest.getDescription());
        Instrument saved = saveInstrument.save(updated);
        return ResponseEntity.ok(mapper.toDetailResponse(saved));
    }

    @Override
    public ResponseEntity<List<InstrumentCandidateResponse>> resolveInstrument(
            ResolveInstrumentRequest resolveInstrumentRequest) {
        List<InstrumentCandidateResponse> candidates =
                resolveInstrument.resolve(resolveInstrumentRequest.getQuery()).stream()
                        .map(mapper::toCandidateResponse)
                        .toList();
        return ResponseEntity.ok(candidates);
    }

    private Instrument findInstrumentOrThrow(UUID id) {
        return loadInstruments.findById(id).orElseThrow(() -> new NotFoundException("instrument", id));
    }
}
