package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.IntradayValuation;
import java.time.Instant;
import java.util.List;

/** Outbound port: read access to stored intraday valuation points. */
public interface LoadValuationsPort {

    /** Ascending by {@code at}. */
    List<IntradayValuation> findBetween(Instant from, Instant to);
}
