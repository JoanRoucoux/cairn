package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.generated.model.EnvelopePerformanceResponse;
import com.roucoux.cairn.generated.model.PerformanceResponse;
import com.roucoux.cairn.generated.model.PerformanceTotalResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Maps the domain model to the generated DTOs. One mapper per resource — never a shared one. The
 * domain never rounds; this is the only place where a monetary amount or a ratio is rounded for
 * the wire.
 */
@Component
public class PerformanceRestMapper {

    private static final int AMOUNT_SCALE = 2;
    private static final int RATIO_SCALE = 6;

    private final ZoneId zone;

    public PerformanceRestMapper(ZoneId zone) {
        this.zone = zone;
    }

    public PerformanceResponse toResponse(Performance performance) {
        PerformanceResponse response = new PerformanceResponse();
        response.setRange(PerformanceResponse.RangeEnum.fromValue(toWireValue(performance.range())));
        response.setFrom(performance.from());
        response.setTo(performance.to());
        response.setReconstructed(performance.reconstructed());
        performance
                .lastPriceAt()
                .ifPresent(
                        instant -> response.setLastPriceAt(instant.atZone(zone).toOffsetDateTime()));
        response.setTotal(toTotal(performance));
        response.setByEnvelope(
                performance.byEnvelope().stream().map(this::toEnvelope).toList());
        return response;
    }

    private PerformanceTotalResponse toTotal(Performance performance) {
        PerformanceTotalResponse total = new PerformanceTotalResponse();
        total.setValueEur(amount(performance.total()));
        total.setChangeEur(amount(performance.change()));
        performance.changeRatio().ifPresent(ratio -> total.setChangeRatio(scaledRatio(ratio)));
        return total;
    }

    private EnvelopePerformanceResponse toEnvelope(EnvelopePerformance envelope) {
        EnvelopePerformanceResponse response = new EnvelopePerformanceResponse();
        response.setAccountType(EnvelopePerformanceResponse.AccountTypeEnum.valueOf(
                envelope.accountType().name()));
        response.setValueEur(amount(envelope.value()));
        response.setShare(envelope.share().setScale(RATIO_SCALE, RoundingMode.HALF_UP));
        response.setChangeEur(amount(envelope.change()));
        envelope.changeRatio().ifPresent(ratio -> response.setChangeRatio(scaledRatio(ratio)));
        return response;
    }

    private static String toWireValue(com.roucoux.cairn.domain.model.PerformanceRange range) {
        return switch (range) {
            case D1 -> "1d";
            case D7 -> "7d";
            case M1 -> "1m";
            case Y1 -> "1y";
            case Y5 -> "5y";
            case MAX -> "max";
        };
    }

    private static BigDecimal amount(Money money) {
        return scaledAmount(money.amount());
    }

    private static BigDecimal scaledAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaledRatio(BigDecimal ratio) {
        return ratio.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
