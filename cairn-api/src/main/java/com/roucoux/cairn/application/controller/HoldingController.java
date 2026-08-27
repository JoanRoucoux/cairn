package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.generated.api.HoldingApi;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.UpdateHoldingRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain's use case. */
@RestController
class HoldingController implements HoldingApi {

    private final ManageHoldingUseCase manageHolding;
    private final LoadHoldingsPort loadHoldings;
    private final HoldingRestMapper mapper;

    HoldingController(ManageHoldingUseCase manageHolding, LoadHoldingsPort loadHoldings, HoldingRestMapper mapper) {
        this.manageHolding = manageHolding;
        this.loadHoldings = loadHoldings;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<HoldingResponse>> listHoldings() {
        List<HoldingResponse> holdings =
                loadHoldings.findAll().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(holdings);
    }

    @Override
    public ResponseEntity<HoldingResponse> createHolding(CreateHoldingRequest createHoldingRequest) {
        Holding holding = manageHolding.create(
                createHoldingRequest.getAccountId(),
                createHoldingRequest.getInstrumentId(),
                createHoldingRequest.getQuantity(),
                createHoldingRequest.getAverageCost());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(holding));
    }

    @Override
    public ResponseEntity<HoldingResponse> updateHolding(UUID id, UpdateHoldingRequest updateHoldingRequest) {
        Holding holding =
                manageHolding.update(id, updateHoldingRequest.getQuantity(), updateHoldingRequest.getAverageCost());
        return ResponseEntity.ok(mapper.toResponse(holding));
    }

    @Override
    public ResponseEntity<Void> deleteHolding(UUID id) {
        manageHolding.delete(id);
        return ResponseEntity.noContent().build();
    }
}
