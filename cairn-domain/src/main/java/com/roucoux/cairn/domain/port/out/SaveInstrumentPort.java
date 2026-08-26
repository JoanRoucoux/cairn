package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Instrument;

/** Outbound port: persist a created or updated instrument. */
public interface SaveInstrumentPort {

    Instrument save(Instrument instrument);
}
