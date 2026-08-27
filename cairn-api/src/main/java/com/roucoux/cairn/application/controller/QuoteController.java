package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.QuoteRestMapper;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.domain.port.in.RecordManualQuoteUseCase;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.generated.api.QuoteApi;
import com.roucoux.cairn.generated.model.QuoteResponse;
import com.roucoux.cairn.generated.model.RecordQuoteRequest;
import com.roucoux.cairn.generated.model.RefreshReportResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the domain. */
@RestController
class QuoteController implements QuoteApi {

    private final LoadQuotesPort loadQuotes;
    private final RecordManualQuoteUseCase recordManualQuote;
    private final RefreshQuotesUseCase refreshQuotes;
    private final QuoteRestMapper mapper;

    QuoteController(
            LoadQuotesPort loadQuotes,
            RecordManualQuoteUseCase recordManualQuote,
            RefreshQuotesUseCase refreshQuotes,
            QuoteRestMapper mapper) {
        this.loadQuotes = loadQuotes;
        this.recordManualQuote = recordManualQuote;
        this.refreshQuotes = refreshQuotes;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<QuoteResponse>> listQuotes(UUID id, LocalDate from, LocalDate to) {
        List<QuoteResponse> quotes = loadQuotes.findBetween(id, from, to).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(quotes);
    }

    @Override
    public ResponseEntity<QuoteResponse> recordQuote(UUID id, RecordQuoteRequest recordQuoteRequest) {
        Quote quote = recordManualQuote.record(id, recordQuoteRequest.getAsOf(), recordQuoteRequest.getPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(quote));
    }

    @Override
    public ResponseEntity<RefreshReportResponse> refreshQuotes() {
        RefreshReport report = refreshQuotes.refreshAll(Set.of(AssetClass.values()));
        return ResponseEntity.ok(mapper.toResponse(report));
    }
}
