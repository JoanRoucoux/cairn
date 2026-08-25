package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Position;
import java.math.BigDecimal;

/** Inbound port: open a position on an instrument, valued at the current market price. */
public interface CreatePositionUseCase {

    Position create(String isin, BigDecimal quantity);
}
