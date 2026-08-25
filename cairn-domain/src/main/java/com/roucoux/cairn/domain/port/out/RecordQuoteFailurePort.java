package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.PriceSource;
import java.util.UUID;

/** Outbound port: record that a quote refresh failed for one instrument. */
public interface RecordQuoteFailurePort {

    void record(UUID instrumentId, PriceSource source, String message);
}
