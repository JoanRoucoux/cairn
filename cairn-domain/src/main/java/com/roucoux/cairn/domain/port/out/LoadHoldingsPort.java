package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Holding;
import java.util.List;

/** Outbound port: read every holding, for portfolio aggregation. */
public interface LoadHoldingsPort {

    List<Holding> findAll();
}
