package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.generated.model.HoldingResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Maps a plain {@link Holding} to the generated DTO for the create/update/delete endpoints. The
 * domain never rounds; this is the only place where a monetary amount is rounded for the wire.
 */
@Component
public class HoldingRestMapper {

    private static final int AMOUNT_SCALE = 2;

    public HoldingResponse toResponse(Holding holding) {
        HoldingResponse response = new HoldingResponse();
        response.setId(holding.id());
        response.setAccountId(holding.accountId());
        response.setInstrumentId(holding.instrumentId());
        response.setQuantity(holding.quantity());
        holding.costBasis().ifPresent(cost -> response.setAverageCost(scaledAmount(cost)));
        return response;
    }

    private static BigDecimal scaledAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
