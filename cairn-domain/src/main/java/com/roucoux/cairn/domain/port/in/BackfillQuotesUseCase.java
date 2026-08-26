package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Instrument;
import java.time.LocalDate;

/** Inbound port: fill in historical quotes for one instrument, from a given date forward. */
public interface BackfillQuotesUseCase {

    int backfill(Instrument instrument, LocalDate from);
}
