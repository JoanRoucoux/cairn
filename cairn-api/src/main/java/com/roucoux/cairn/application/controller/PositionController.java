package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.PositionRestMapper;
import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.in.CreatePositionUseCase;
import com.roucoux.cairn.domain.port.in.GetPositionUseCase;
import com.roucoux.cairn.generated.api.PositionApi;
import com.roucoux.cairn.generated.model.CreatePositionRequest;
import com.roucoux.cairn.generated.model.PositionResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain's use cases. */
@RestController
class PositionController implements PositionApi {

    private final CreatePositionUseCase createPosition;
    private final GetPositionUseCase getPosition;
    private final PositionRestMapper mapper;

    PositionController(
            CreatePositionUseCase createPosition, GetPositionUseCase getPosition, PositionRestMapper mapper) {
        this.createPosition = createPosition;
        this.getPosition = getPosition;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PositionResponse> createPosition(CreatePositionRequest request) {
        Position position = createPosition.create(request.getIsin(), request.getQuantity());
        return ResponseEntity.created(URI.create("/position/" + position.id())).body(mapper.toResponse(position));
    }

    @Override
    public ResponseEntity<PositionResponse> getPositionById(UUID id) {
        return getPosition
                .byId(id)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
