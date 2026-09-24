package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.PerformanceRestMapper;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.generated.api.PerformanceApi;
import com.roucoux.cairn.generated.model.PerformanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Inbound adapter: implements the generated contract and delegates to the domain's use case. */
@RestController
class PerformanceController implements PerformanceApi {

    private final GetPerformanceUseCase getPerformance;
    private final PerformanceRestMapper mapper;

    PerformanceController(GetPerformanceUseCase getPerformance, PerformanceRestMapper mapper) {
        this.getPerformance = getPerformance;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PerformanceResponse> getPortfolioPerformance(String range) {
        try {
            return ResponseEntity.ok(mapper.toResponse(getPerformance.performance(mapper.toPerformanceRange(range))));
        } catch (IllegalArgumentException unknownRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, unknownRange.getMessage());
        }
    }
}
