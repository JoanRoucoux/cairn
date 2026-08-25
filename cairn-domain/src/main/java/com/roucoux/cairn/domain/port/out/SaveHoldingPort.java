package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Holding;

/** Outbound port: persist a created or updated holding. */
public interface SaveHoldingPort {

    Holding save(Holding holding);
}
