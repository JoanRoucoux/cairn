package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.InstrumentCandidate;
import java.util.List;

public interface ResolveInstrumentUseCase {

    List<InstrumentCandidate> resolve(String query);
}
