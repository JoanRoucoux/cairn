package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Position;

/** Outbound port: write access to stored positions. */
public interface SavePositionPort {

    Position save(Position position);
}
