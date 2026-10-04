package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.domain.exception.business.InvalidInstrumentException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.NewInstrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.generated.api.HoldingApi;
import com.roucoux.cairn.generated.model.BuyHoldingRequest;
import com.roucoux.cairn.generated.model.ChangeHoldingInstrumentRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.NewInstrumentRequest;
import com.roucoux.cairn.generated.model.SellHoldingRequest;
import com.roucoux.cairn.generated.model.UpdateHoldingRequest;
import com.roucoux.cairn.infrastructure.transaction.HoldingTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HoldingController implements HoldingApi {

    private final ManageHoldingUseCase manageHolding;
    private final HoldingTransaction holdingTransaction;
    private final LoadHoldingsPort loadHoldings;
    private final ValueHoldingUseCase valueHolding;
    private final HoldingRestMapper mapper;

    HoldingController(
            ManageHoldingUseCase manageHolding,
            HoldingTransaction holdingTransaction,
            LoadHoldingsPort loadHoldings,
            ValueHoldingUseCase valueHolding,
            HoldingRestMapper mapper) {
        this.manageHolding = manageHolding;
        this.holdingTransaction = holdingTransaction;
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
        Holding holding = create(createHoldingRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(holding));
    }

    @Override
    public ResponseEntity<HoldingResponse> updateHolding(UUID id, UpdateHoldingRequest updateHoldingRequest) {
        Holding holding =
                manageHolding.update(id, updateHoldingRequest.getQuantity(), updateHoldingRequest.getAverageCost());
        return ResponseEntity.ok(toResponse(holding));
    }

    @Override
    public ResponseEntity<HoldingResponse> changeHoldingInstrument(UUID id, ChangeHoldingInstrumentRequest request) {
        return ResponseEntity.ok(toResponse(manageHolding.changeInstrument(id, request.getInstrumentId())));
    }

    @Override
    public ResponseEntity<Void> deleteHolding(UUID id) {
        holdingTransaction.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<HoldingResponse> buyHolding(UUID id, BuyHoldingRequest buyHoldingRequest) {
        Holding holding = manageHolding.buy(id, buyHoldingRequest.getQuantity(), buyHoldingRequest.getUnitPrice());
        return ResponseEntity.ok(toResponse(holding));
    }

    @Override
    public ResponseEntity<HoldingResponse> sellHolding(UUID id, SellHoldingRequest sellHoldingRequest) {
        return holdingTransaction
                .sell(id, sellHoldingRequest.getQuantity())
                .map(remaining -> ResponseEntity.ok(toResponse(remaining)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private Holding create(CreateHoldingRequest request) {
        NewInstrumentRequest instrument = request.getInstrument();
        if ((request.getInstrumentId() == null) == (instrument == null)) {
            throw new InvalidInstrumentException("exactly one of instrumentId and instrument is required");
        }
        if (instrument == null) {
            return holdingTransaction.create(
                    request.getAccountId(), request.getInstrumentId(), request.getQuantity(), request.getAverageCost());
        }
        return holdingTransaction.createWithNewInstrument(
                request.getAccountId(),
                new NewInstrument(
                        instrument.getName(),
                        AssetClass.valueOf(instrument.getAssetClass().name()),
                        PriceSource.valueOf(instrument.getPriceSource().name()),
                        instrument.getSourceRef(),
                        instrument.getIsin(),
                        instrument.getSymbol(),
                        instrument.getPrice()),
                request.getQuantity(),
                request.getAverageCost());
    }

    private HoldingResponse toResponse(Holding holding) {
        return valueHolding.value(holding).map(mapper::toResponse).orElseGet(() -> mapper.toResponse(holding));
    }
}
