package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.IntradayPoint;
import com.roucoux.cairn.generated.model.HistoryResponse;
import com.roucoux.cairn.generated.model.IntradayHistoryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HistoryRestMapper {

    private static final int AMOUNT_SCALE = 2;

    public HistoryResponse toResponse(HistoryMode mode, List<com.roucoux.cairn.domain.model.HistoryPoint> points) {
        HistoryResponse response = new HistoryResponse();
        response.setMode(HistoryResponse.ModeEnum.fromValue(toWireValue(mode)));
        response.setReconstructed(mode == HistoryMode.CONSTANT_MIX);
        response.setPoints(points.stream().map(this::toPoint).toList());
        return response;
    }

    private com.roucoux.cairn.generated.model.HistoryPoint toPoint(com.roucoux.cairn.domain.model.HistoryPoint point) {
        com.roucoux.cairn.generated.model.HistoryPoint dto = new com.roucoux.cairn.generated.model.HistoryPoint();
        dto.setDate(point.date());
        dto.setTotalEur(scaledAmount(point.totalEur()));
        return dto;
    }

    public IntradayHistoryResponse toIntradayResponse(List<IntradayPoint> points) {
        IntradayHistoryResponse response = new IntradayHistoryResponse();
        response.setPoints(points.stream().map(this::toIntradayHistoryPoint).toList());
        return response;
    }

    private com.roucoux.cairn.generated.model.IntradayHistoryPoint toIntradayHistoryPoint(IntradayPoint point) {
        com.roucoux.cairn.generated.model.IntradayHistoryPoint dto =
                new com.roucoux.cairn.generated.model.IntradayHistoryPoint();
        dto.setAt(point.at().atOffset(ZoneOffset.UTC));
        dto.setTotalEur(scaledAmount(point.totalEur()));
        return dto;
    }

    private static String toWireValue(HistoryMode mode) {
        return mode == HistoryMode.CONSTANT_MIX ? "constant-mix" : "snapshot";
    }

    private static BigDecimal scaledAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
