package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import java.util.List;

public interface SearchInstrumentsUseCase {

    List<InstrumentCandidate> search(PriceSource source, String query);
}
