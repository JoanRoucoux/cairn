package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.ValuedHolding;
import java.util.Optional;

/**
 * Inbound port: resolve a single {@link Holding} into a fully valued {@link ValuedHolding} by
 * joining it with its account, instrument and latest quote. Empty when any of these is missing —
 * most commonly a brand-new instrument whose quote has not been fetched yet.
 */
public interface ValueHoldingUseCase {

    Optional<ValuedHolding> value(Holding holding);
}
