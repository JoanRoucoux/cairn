package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;

public interface GetPerformanceUseCase {

    Performance performance(PerformanceRange range);
}
