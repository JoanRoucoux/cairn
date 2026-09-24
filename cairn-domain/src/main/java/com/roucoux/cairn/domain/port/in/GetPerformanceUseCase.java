package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;

/** Inbound port: the portfolio's current value and its change over a range, per envelope. */
public interface GetPerformanceUseCase {

    Performance performance(PerformanceRange range);
}
