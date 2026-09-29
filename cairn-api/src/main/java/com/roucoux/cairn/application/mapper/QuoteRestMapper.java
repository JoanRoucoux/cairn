package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.generated.model.PriceSource;
import com.roucoux.cairn.generated.model.QuoteResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class QuoteRestMapper {

    private static final int PRICE_SCALE = 2;

    public QuoteResponse toResponse(Quote quote) {
        QuoteResponse response = new QuoteResponse();
        response.setInstrumentId(quote.instrumentId());
        response.setAsOf(quote.asOf());
        response.setPrice(scaledPrice(quote.price()));
        response.setCurrency(quote.currency());
        response.setSource(PriceSource.valueOf(quote.source().name()));
        return response;
    }

    private static BigDecimal scaledPrice(BigDecimal price) {
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
