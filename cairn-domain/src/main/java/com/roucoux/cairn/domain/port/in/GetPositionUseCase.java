package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Position;
import java.util.Optional;
import java.util.UUID;

/** Inbound port: read a position back. */
public interface GetPositionUseCase {

    Optional<Position> byId(UUID id);
}
