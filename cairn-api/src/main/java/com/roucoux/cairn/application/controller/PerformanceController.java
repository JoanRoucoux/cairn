package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.PerformanceRestMapper;
import com.roucoux.cairn.domain.model.PerformanceRange;
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
        return ResponseEntity.ok(mapper.toResponse(getPerformance.performance(toPerformanceRange(range))));
    }

    private static PerformanceRange toPerformanceRange(String range) {
        return switch (range) {
            case "1d" -> PerformanceRange.D1;
            case "7d" -> PerformanceRange.D7;
            case "1m" -> PerformanceRange.M1;
            case "1y" -> PerformanceRange.Y1;
            case "5y" -> PerformanceRange.Y5;
            case "max" -> PerformanceRange.MAX;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown range '" + range + "'");
        };
    }
}
