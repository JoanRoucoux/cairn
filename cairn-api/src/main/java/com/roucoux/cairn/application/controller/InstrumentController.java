package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.InstrumentRestMapper;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ManageInstrumentUseCase;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.generated.api.InstrumentApi;
import com.roucoux.cairn.generated.model.CreateInstrumentRequest;
import com.roucoux.cairn.generated.model.InstrumentCandidateResponse;
import com.roucoux.cairn.generated.model.InstrumentDetailResponse;
import com.roucoux.cairn.generated.model.InstrumentResponse;
import com.roucoux.cairn.generated.model.ResolveInstrumentRequest;
import com.roucoux.cairn.generated.model.UpdateInstrumentRequest;
import com.roucoux.cairn.infrastructure.transaction.InstrumentDeletionTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain. */
@RestController
class InstrumentController implements InstrumentApi {

    private final LoadInstrumentsPort loadInstruments;
    private final LoadHoldingsPort loadHoldings;
    private final ManageInstrumentUseCase manageInstrument;
    private final InstrumentDeletionTransaction deleteInstrumentTransaction;
    private final ResolveInstrumentUseCase resolveInstrument;
    private final InstrumentRestMapper mapper;

    InstrumentController(
            LoadInstrumentsPort loadInstruments,
            LoadHoldingsPort loadHoldings,
            ManageInstrumentUseCase manageInstrument,
            InstrumentDeletionTransaction deleteInstrumentTransaction,
            ResolveInstrumentUseCase resolveInstrument,
            InstrumentRestMapper mapper) {
        this.loadInstruments = loadInstruments;
        this.loadHoldings = loadHoldings;
        this.manageInstrument = manageInstrument;
        this.deleteInstrumentTransaction = deleteInstrumentTransaction;
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
        Instrument created = manageInstrument.create(
                createInstrumentRequest.getName(),
                createInstrumentRequest.getIsin(),
                createInstrumentRequest.getCurrency(),
                AssetClass.valueOf(createInstrumentRequest.getAssetClass().name()),
                PriceSource.valueOf(createInstrumentRequest.getPriceSource().name()),
                createInstrumentRequest.getSourceRef(),
                createInstrumentRequest.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @Override
    public ResponseEntity<InstrumentDetailResponse> getInstrument(UUID id) {
        Instrument instrument = findInstrumentOrThrow(id);
        return ResponseEntity.ok(toDetailResponse(instrument));
    }

    @Override
    public ResponseEntity<InstrumentDetailResponse> updateInstrument(
            UUID id, UpdateInstrumentRequest updateInstrumentRequest) {
        Instrument updated = manageInstrument.update(
                id,
                updateInstrumentRequest.getName(),
                updateInstrumentRequest.getIsin(),
                AssetClass.valueOf(updateInstrumentRequest.getAssetClass().name()),
                PriceSource.valueOf(updateInstrumentRequest.getPriceSource().name()),
                updateInstrumentRequest.getSourceRef(),
                updateInstrumentRequest.getDescription());
        return ResponseEntity.ok(toDetailResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deleteInstrument(UUID id) {
        deleteInstrumentTransaction.run(id);
        return ResponseEntity.noContent().build();
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

    private InstrumentDetailResponse toDetailResponse(Instrument instrument) {
        int holdingCount = loadHoldings.findByInstrument(instrument.id()).size();
        return mapper.toDetailResponse(instrument, holdingCount);
    }
}
