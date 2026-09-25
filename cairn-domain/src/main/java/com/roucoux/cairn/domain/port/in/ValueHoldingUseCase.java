package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.ValuedHolding;
import java.util.Optional;

public interface ValueHoldingUseCase {

    Optional<ValuedHolding> value(Holding holding);
}
