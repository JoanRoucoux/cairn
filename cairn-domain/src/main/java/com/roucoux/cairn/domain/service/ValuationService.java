package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.IntradayValuation;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.RecordValuationUseCase;
import com.roucoux.cairn.domain.port.out.SaveValuationPort;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ValuationService implements RecordValuationUseCase {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final GetPortfolioUseCase getPortfolio;
    private final SaveValuationPort saveValuation;

    public ValuationService(GetPortfolioUseCase getPortfolio, SaveValuationPort saveValuation) {
        this.getPortfolio = getPortfolio;
        this.saveValuation = saveValuation;
    }

    @Override
    public IntradayValuation record(Instant at) {
        Instant truncated = at.truncatedTo(ChronoUnit.MINUTES);
        Portfolio portfolio = getPortfolio.get();
        IntradayValuation valuation =
                new IntradayValuation(truncated, portfolio.total().amount());

        saveValuation.upsert(valuation);
        saveValuation.deleteBefore(truncated.minus(RETENTION));

        return valuation;
    }
}
