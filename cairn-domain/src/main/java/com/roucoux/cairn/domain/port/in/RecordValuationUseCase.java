package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.IntradayValuation;
import java.time.Instant;

/** Inbound port: record one point of the portfolio's value and announce it. */
public interface RecordValuationUseCase {

    IntradayValuation record(Instant at);
}
