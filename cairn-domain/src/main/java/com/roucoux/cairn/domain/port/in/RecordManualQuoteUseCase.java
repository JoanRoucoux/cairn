package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Quote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Inbound port: record a quote entered by hand, for an instrument with no automated source. */
public interface RecordManualQuoteUseCase {

    Quote record(UUID instrumentId, LocalDate asOf, BigDecimal price);
}
