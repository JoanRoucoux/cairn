package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.IntradayValuation;
import java.time.Instant;
import java.util.List;

public interface LoadValuationsPort {

    List<IntradayValuation> findBetween(Instant from, Instant to);
}
