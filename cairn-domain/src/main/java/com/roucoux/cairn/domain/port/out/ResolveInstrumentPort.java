package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import java.util.List;

public interface ResolveInstrumentPort {

    boolean supports(PriceSource source);

    List<InstrumentCandidate> resolve(String query);
}
