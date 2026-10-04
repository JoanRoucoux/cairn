package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.InstrumentRestMapper;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.SearchInstrumentsUseCase;
import com.roucoux.cairn.generated.api.InstrumentApi;
import com.roucoux.cairn.generated.model.InstrumentCandidateResponse;
import com.roucoux.cairn.generated.model.SearchableSource;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InstrumentController implements InstrumentApi {

    private final SearchInstrumentsUseCase searchInstruments;
    private final InstrumentRestMapper mapper;

    InstrumentController(SearchInstrumentsUseCase searchInstruments, InstrumentRestMapper mapper) {
        this.searchInstruments = searchInstruments;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<InstrumentCandidateResponse>> searchInstruments(SearchableSource source, String query) {
        List<InstrumentCandidateResponse> candidates =
                searchInstruments.search(PriceSource.valueOf(source.name()), query).stream()
                        .map(mapper::toCandidateResponse)
                        .toList();
        return ResponseEntity.ok(candidates);
    }
}
