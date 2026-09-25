package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Holding;

public interface SaveHoldingPort {

    Holding save(Holding holding);
}
