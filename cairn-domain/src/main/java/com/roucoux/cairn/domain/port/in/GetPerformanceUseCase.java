package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import java.time.LocalDate;

public interface GetPerformanceUseCase {

    Performance performance(PerformanceRange range);

    Performance performanceSince(LocalDate baseline);
}
