package com.roucoux.cairn.domain.port.out;

import java.util.UUID;

/** Outbound port: remove an instrument. */
public interface DeleteInstrumentPort {

    void delete(UUID id);
}
