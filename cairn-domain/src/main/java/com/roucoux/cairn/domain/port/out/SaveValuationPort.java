package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.IntradayValuation;
import java.time.Instant;

/** Outbound port: persist intraday valuation points. */
public interface SaveValuationPort {

    void upsert(IntradayValuation valuation);

    void deleteBefore(Instant cutoff);
}
