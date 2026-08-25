package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Position;
import java.util.List;

/** Outbound port: read every position still open, for bulk processing. */
public interface LoadOpenPositionsPort {

    List<Position> findOpen();
}
