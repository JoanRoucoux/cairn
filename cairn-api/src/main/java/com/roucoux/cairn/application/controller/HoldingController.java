package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
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
    private final ValueHoldingUseCase valueHolding;
    private final HoldingRestMapper mapper;

    HoldingController(
            ManageHoldingUseCase manageHolding,
            LoadHoldingsPort loadHoldings,
            ValueHoldingUseCase valueHolding,
            HoldingRestMapper mapper) {
        this.manageHolding = manageHolding;
        this.loadHoldings = loadHoldings;
        this.valueHolding = valueHolding;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<HoldingResponse>> listHoldings() {
        List<HoldingResponse> holdings = loadHoldings.findAll().stream()
                .flatMap(holding -> valueHolding.value(holding).stream())
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(holdings);
    }

    @Override
    public ResponseEntity<HoldingResponse> createHolding(CreateHoldingRequest createHoldingRequest) {
        Holding holding = manageHolding.create(
                createHoldingRequest.getAccountId(),
                createHoldingRequest.getInstrumentId(),
                createHoldingRequest.getQuantity(),
                createHoldingRequest.getAverageCost());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(holding));
    }

    @Override
    public ResponseEntity<HoldingResponse> updateHolding(UUID id, UpdateHoldingRequest updateHoldingRequest) {
        Holding holding =
                manageHolding.update(id, updateHoldingRequest.getQuantity(), updateHoldingRequest.getAverageCost());
        return ResponseEntity.ok(toResponse(holding));
    }

    @Override
    public ResponseEntity<Void> deleteHolding(UUID id) {
        manageHolding.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Best-effort full valuation: falls back to the bare-holding shape only when the instrument
     * has no quote yet (e.g. just added, not refreshed), so market-data fields stay genuinely
     * absent rather than fabricated.
     */
    private HoldingResponse toResponse(Holding holding) {
        return valueHolding.value(holding).map(mapper::toResponse).orElseGet(() -> mapper.toResponse(holding));
    }
}
