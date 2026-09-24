package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.IntradayPoint;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.port.in.GetIntradayHistoryUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.out.LoadValuationsPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class IntradayHistoryService implements GetIntradayHistoryUseCase {

    private final GetPortfolioUseCase getPortfolio;
    private final LoadValuationsPort loadValuations;
    private final Clock clock;

    public IntradayHistoryService(GetPortfolioUseCase getPortfolio, LoadValuationsPort loadValuations, Clock clock) {
        this.getPortfolio = getPortfolio;
        this.loadValuations = loadValuations;
        this.clock = clock;
    }

    @Override
    public List<IntradayPoint> intraday(LocalDate date, ZoneId zone) {
        Instant startOfDay = date.atStartOfDay(zone).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<IntradayPoint> recorded = loadValuations.findBetween(startOfDay, endOfDay).stream()
                .map(valuation -> new IntradayPoint(valuation.at(), valuation.totalEur()))
                .toList();

        boolean isToday = date.equals(LocalDate.now(clock.withZone(zone)));
        if (!isToday) {
            return recorded;
        }

        Portfolio portfolio = getPortfolio.get();
        Instant now = clock.instant();
        List<IntradayPoint> points = new ArrayList<>();
        points.add(new IntradayPoint(
                startOfDay, portfolio.total().minus(portfolio.dayChange()).amount()));
        recorded.stream()
                .filter(point -> point.at().isAfter(startOfDay) && !point.at().isAfter(now))
                .forEach(points::add);
        points.add(new IntradayPoint(now, portfolio.total().amount()));
        return points;
    }
}
