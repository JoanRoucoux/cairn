package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Holding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: read every holding, for portfolio aggregation. */
public interface LoadHoldingsPort {

    List<Holding> findAll();

    Optional<Holding> findById(UUID id);

    Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId);
}
