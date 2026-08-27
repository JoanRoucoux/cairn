package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.generated.model.QuoteResponse;
import com.roucoux.cairn.generated.model.RefreshFailureResponse;
import com.roucoux.cairn.generated.model.RefreshReportResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Maps a plain {@link Quote} or {@link RefreshReport} to the generated DTOs. The domain never
 * rounds; this is the only place where a price is rounded for the wire.
 */
@Component
public class QuoteRestMapper {

    private static final int PRICE_SCALE = 2;

    public QuoteResponse toResponse(Quote quote) {
        QuoteResponse response = new QuoteResponse();
        response.setInstrumentId(quote.instrumentId());
        response.setAsOf(quote.asOf());
        response.setPrice(scaledPrice(quote.price()));
        response.setCurrency(quote.currency());
        response.setSource(QuoteResponse.SourceEnum.valueOf(quote.source().name()));
        return response;
    }

    public RefreshReportResponse toResponse(RefreshReport report) {
        RefreshReportResponse response = new RefreshReportResponse();
        response.setRefreshed(report.refreshed());
        response.setSkipped(report.skipped());
        response.setFailures(
                report.failures().stream().map(this::toFailureResponse).toList());
        return response;
    }

    private RefreshFailureResponse toFailureResponse(RefreshReport.Failure failure) {
        RefreshFailureResponse response = new RefreshFailureResponse();
        response.setInstrumentId(failure.instrumentId());
        response.setInstrumentName(failure.instrumentName());
        response.setSource(
                RefreshFailureResponse.SourceEnum.valueOf(failure.source().name()));
        response.setMessage(failure.message());
        return response;
    }

    private static BigDecimal scaledPrice(BigDecimal price) {
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
