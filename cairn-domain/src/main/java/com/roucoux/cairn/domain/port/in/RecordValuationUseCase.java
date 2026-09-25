package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.IntradayValuation;
import java.time.Instant;

public interface RecordValuationUseCase {

    IntradayValuation record(Instant at);
}
