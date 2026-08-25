package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.QuoteRestMapper;
import com.roucoux.cairn.domain.port.in.GetQuoteUseCase;
import com.roucoux.cairn.generated.api.QuoteApi;
import com.roucoux.cairn.generated.model.QuoteResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain's use cases. */
@RestController
class QuoteController implements QuoteApi {

    private final GetQuoteUseCase getQuote;
    private final QuoteRestMapper mapper;

    QuoteController(GetQuoteUseCase getQuote, QuoteRestMapper mapper) {
        this.getQuote = getQuote;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<QuoteResponse> getQuoteByIsin(String isin) {
        return ResponseEntity.ok(mapper.toResponse(getQuote.byIsin(isin)));
    }
}
