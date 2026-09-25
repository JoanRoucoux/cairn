package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Quote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface RecordManualQuoteUseCase {

    Quote record(UUID instrumentId, LocalDate asOf, BigDecimal price);
}
