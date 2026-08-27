package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.generated.model.HistoryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps the domain model to the generated DTOs. One mapper per resource — never a shared one. The
 * domain never rounds; this is the only place where a monetary amount is rounded for the wire.
 *
 * <p>The domain's {@code HistoryPoint} and the generated {@code HistoryPoint} DTO share a name by
 * design — the wire schema mirrors the domain model — so both are referenced by their fully
 * qualified name here instead of importing either.
 */
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

    private static String toWireValue(HistoryMode mode) {
        return mode == HistoryMode.CONSTANT_MIX ? "constant-mix" : "snapshot";
    }

    private static BigDecimal scaledAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
