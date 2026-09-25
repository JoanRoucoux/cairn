package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.PriceSource;
import java.util.UUID;

public interface RecordQuoteFailurePort {

    void record(UUID instrumentId, PriceSource source, String message);
}
