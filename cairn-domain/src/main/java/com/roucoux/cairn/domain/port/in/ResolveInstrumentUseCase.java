package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.InstrumentCandidate;
import java.util.List;

/** Inbound port: resolve a query (typically an ISIN) into candidate instruments across every source. */
public interface ResolveInstrumentUseCase {

    List<InstrumentCandidate> resolve(String query);
}
