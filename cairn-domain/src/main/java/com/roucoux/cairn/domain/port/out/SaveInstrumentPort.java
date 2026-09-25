package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Instrument;

public interface SaveInstrumentPort {

    Instrument save(Instrument instrument);
}
