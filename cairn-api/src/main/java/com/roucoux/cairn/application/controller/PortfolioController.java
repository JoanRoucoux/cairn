package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.PortfolioRestMapper;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.generated.api.PortfolioApi;
import com.roucoux.cairn.generated.model.PortfolioResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain's use case. */
@RestController
class PortfolioController implements PortfolioApi {

    private final GetPortfolioUseCase getPortfolio;
    private final PortfolioRestMapper mapper;

    PortfolioController(GetPortfolioUseCase getPortfolio, PortfolioRestMapper mapper) {
        this.getPortfolio = getPortfolio;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PortfolioResponse> getPortfolio() {
        return ResponseEntity.ok(mapper.toResponse(getPortfolio.get()));
    }
}
