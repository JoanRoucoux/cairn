package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.HistoryRestMapper;
import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.port.in.GetHistoryUseCase;
import com.roucoux.cairn.domain.port.in.GetIntradayHistoryUseCase;
import com.roucoux.cairn.generated.api.HistoryApi;
import com.roucoux.cairn.generated.model.HistoryResponse;
import com.roucoux.cairn.generated.model.IntradayHistoryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class HistoryController implements HistoryApi {

    private static final LocalDate EARLIEST_FROM = LocalDate.of(1900, 1, 1);

    private final GetHistoryUseCase getHistory;
    private final GetIntradayHistoryUseCase getIntradayHistory;
    private final HistoryRestMapper mapper;
    private final ZoneId zone;
    private final Clock clock;

    HistoryController(
            GetHistoryUseCase getHistory,
            GetIntradayHistoryUseCase getIntradayHistory,
            HistoryRestMapper mapper,
            ZoneId zone,
            Clock clock) {
        this.clock = clock;
        this.getHistory = getHistory;
        this.getIntradayHistory = getIntradayHistory;
        this.mapper = mapper;
        this.zone = zone;
    }

    @Override
    public ResponseEntity<HistoryResponse> getHistory(String mode, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must not precede 'from'");
        }
        if (from.isBefore(EARLIEST_FROM)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not precede " + EARLIEST_FROM);
        }
        HistoryMode historyMode = toHistoryMode(mode);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (from.isAfter(today)) {
            return ResponseEntity.ok(mapper.toResponse(historyMode, List.of()));
        }
        LocalDate end = to.isAfter(today) ? today : to;
        return ResponseEntity.ok(mapper.toResponse(historyMode, getHistory.history(historyMode, from, end)));
    }

    @Override
    public ResponseEntity<IntradayHistoryResponse> getIntradayHistory(LocalDate date) {
        return ResponseEntity.ok(mapper.toIntradayResponse(getIntradayHistory.intraday(date, zone)));
    }

    private static HistoryMode toHistoryMode(String mode) {
        try {
            return HistoryMode.valueOf(mode.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown mode");
        }
    }
}
