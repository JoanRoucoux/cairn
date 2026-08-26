package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import java.util.List;

/** Outbound port: resolve a query (typically an ISIN) into candidates from one price source. */
public interface ResolveInstrumentPort {

    boolean supports(PriceSource source);

    List<InstrumentCandidate> resolve(String query);
}
