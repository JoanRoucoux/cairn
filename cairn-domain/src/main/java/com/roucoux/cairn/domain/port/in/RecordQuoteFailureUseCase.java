package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.PriceSource;
import java.util.UUID;

public interface RecordQuoteFailureUseCase {

    void record(UUID instrumentId, PriceSource source, String message);

    void purgeExpired();
}
