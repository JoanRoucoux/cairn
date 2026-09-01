package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.csv.HoldingCsvWriter;
import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.application.mapper.PortfolioRestMapper;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.generated.api.PortfolioApi;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.PortfolioResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain's use case. */
@RestController
class PortfolioController implements PortfolioApi {

    private final GetPortfolioUseCase getPortfolio;
    private final LoadHoldingsPort loadHoldings;
    private final ValueHoldingUseCase valueHolding;
    private final PortfolioRestMapper mapper;
    private final HoldingRestMapper holdingMapper;
    private final HoldingCsvWriter csvWriter;
    private final Clock clock;

    PortfolioController(
            GetPortfolioUseCase getPortfolio,
            LoadHoldingsPort loadHoldings,
            ValueHoldingUseCase valueHolding,
            PortfolioRestMapper mapper,
            HoldingRestMapper holdingMapper,
            HoldingCsvWriter csvWriter,
            Clock clock) {
        this.getPortfolio = getPortfolio;
        this.loadHoldings = loadHoldings;
        this.valueHolding = valueHolding;
        this.mapper = mapper;
        this.holdingMapper = holdingMapper;
        this.csvWriter = csvWriter;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<PortfolioResponse> getPortfolio() {
        return ResponseEntity.ok(mapper.toResponse(getPortfolio.get()));
    }

    @Override
    public ResponseEntity<String> exportPortfolio() {
        List<HoldingResponse> holdings = loadHoldings.findAll().stream()
                .flatMap(holding -> valueHolding.value(holding).stream())
                .map(holdingMapper::toResponse)
                .toList();
        String filename = "cairn-" + LocalDate.now(clock) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvWriter.write(holdings));
    }
}
