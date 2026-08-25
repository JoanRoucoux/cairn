package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Position;

/** Inbound port: value a position again, at the price the market shows now. */
public interface RevaluePositionUseCase {

    Position revalue(Position position);
}
