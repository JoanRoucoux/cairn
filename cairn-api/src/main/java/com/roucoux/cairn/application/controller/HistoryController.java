package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.HistoryRestMapper;
import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.port.in.GetHistoryUseCase;
import com.roucoux.cairn.generated.api.HistoryApi;
import com.roucoux.cairn.generated.model.HistoryResponse;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Inbound adapter: implements the generated contract and delegates to the domain's use case. */
@RestController
class HistoryController implements HistoryApi {

    private final GetHistoryUseCase getHistory;
    private final HistoryRestMapper mapper;

    HistoryController(GetHistoryUseCase getHistory, HistoryRestMapper mapper) {
        this.getHistory = getHistory;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<HistoryResponse> getHistory(String mode, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must not precede 'from'");
        }
        HistoryMode historyMode = toHistoryMode(mode);
        return ResponseEntity.ok(mapper.toResponse(historyMode, getHistory.history(historyMode, from, to)));
    }

    private static HistoryMode toHistoryMode(String mode) {
        return HistoryMode.valueOf(mode.toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
