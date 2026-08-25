package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.generated.model.PositionResponse;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Maps the domain model to the generated DTOs. One mapper per resource — never a shared one. */
@Component
public class PositionRestMapper {

    public PositionResponse toResponse(Position position) {
        PositionResponse response = new PositionResponse();
        response.setId(position.id());
        response.setIsin(position.isin());
        response.setQuantity(position.quantity());
        response.setPrice(position.price());
        response.setStatus(PositionResponse.StatusEnum.valueOf(position.status().name()));
        response.setCreatedAt(position.createdAt().atOffset(ZoneOffset.UTC));
        return response;
    }
}
