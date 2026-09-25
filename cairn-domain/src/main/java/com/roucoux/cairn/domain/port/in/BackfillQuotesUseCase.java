package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Instrument;
import java.time.LocalDate;

public interface BackfillQuotesUseCase {

    int backfill(Instrument instrument, LocalDate from);
}
