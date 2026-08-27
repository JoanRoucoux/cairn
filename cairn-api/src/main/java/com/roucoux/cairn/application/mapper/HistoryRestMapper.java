package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.HistoryPoint;
import com.roucoux.cairn.generated.model.HistoryResponse;
import com.roucoux.cairn.generated.model.HistoryResponsePointsInner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps the domain model to the generated DTOs. One mapper per resource — never a shared one. The
 * domain never rounds; this is the only place where a monetary amount is rounded for the wire.
 */
@Component
public class HistoryRestMapper {

    private static final int AMOUNT_SCALE = 2;

    public HistoryResponse toResponse(HistoryMode mode, List<HistoryPoint> points) {
        HistoryResponse response = new HistoryResponse();
        response.setMode(HistoryResponse.ModeEnum.fromValue(toWireValue(mode)));
        response.setReconstructed(mode == HistoryMode.CONSTANT_MIX);
        response.setPoints(points.stream().map(this::toPoint).toList());
        return response;
    }

    private HistoryResponsePointsInner toPoint(HistoryPoint point) {
        HistoryResponsePointsInner inner = new HistoryResponsePointsInner();
        inner.setDate(point.date());
        inner.setTotalEur(scaledAmount(point.totalEur()).doubleValue());
        return inner;
    }

    private static String toWireValue(HistoryMode mode) {
        return mode == HistoryMode.CONSTANT_MIX ? "constant-mix" : "snapshot";
    }

    private static BigDecimal scaledAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
