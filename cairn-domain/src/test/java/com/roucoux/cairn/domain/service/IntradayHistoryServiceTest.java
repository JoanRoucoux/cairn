package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Allocation;
import com.roucoux.cairn.domain.model.IntradayPoint;
import com.roucoux.cairn.domain.model.IntradayValuation;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.port.out.LoadValuationsPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntradayHistoryServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static final Portfolio PORTFOLIO = new Portfolio(
            Money.eur(new BigDecimal("110000")),
            Money.eur(new BigDecimal("2000")),
            Optional.empty(),
            List.<Allocation>of(),
            List.<Allocation>of(),
            List.of(),
            0,
            0);

    @Test
    void returnsOnlyTheRecordedPointsForAPastDay() {
        LocalDate pastDay = LocalDate.of(2026, 9, 20);
        Instant recordedAt = pastDay.atStartOfDay(PARIS).plusHours(10).toInstant();
        LoadValuationsPort loadValuations =
                (from, to) -> List.of(new IntradayValuation(recordedAt, new BigDecimal("108500")));
        Clock clock = Clock.fixed(pastDay.plusDays(4).atStartOfDay(PARIS).toInstant(), PARIS);
        IntradayHistoryService service = new IntradayHistoryService(() -> PORTFOLIO, loadValuations, clock);

        List<IntradayPoint> points = service.intraday(pastDay, PARIS);

        assertThat(points).containsExactly(new IntradayPoint(recordedAt, new BigDecimal("108500")));
    }

    @Test
    void aRecordedPointAtTodaysStartOfDayDoesNotDuplicateTheOpeningPoint() {
        LocalDate today = LocalDate.of(2026, 9, 24);
        Instant startOfDay = today.atStartOfDay(PARIS).toInstant();
        Instant now = today.atStartOfDay(PARIS).plusHours(14).toInstant();
        Clock clock = Clock.fixed(now, PARIS);
        LoadValuationsPort loadValuations =
                (from, to) -> List.of(new IntradayValuation(startOfDay, new BigDecimal("108000")));
        IntradayHistoryService service = new IntradayHistoryService(() -> PORTFOLIO, loadValuations, clock);

        List<IntradayPoint> points = service.intraday(today, PARIS);

        assertThat(points)
                .containsExactly(
                        new IntradayPoint(startOfDay, new BigDecimal("108000")),
                        new IntradayPoint(now, new BigDecimal("110000")));
    }

    @Test
    void returnsTwoPointsForTodayWhenNothingIsRecordedYet() {
        LocalDate today = LocalDate.of(2026, 9, 24);
        Instant now = today.atStartOfDay(PARIS).plusHours(9).toInstant();
        Clock clock = Clock.fixed(now, PARIS);
        LoadValuationsPort loadValuations = (from, to) -> List.of();
        IntradayHistoryService service = new IntradayHistoryService(() -> PORTFOLIO, loadValuations, clock);

        List<IntradayPoint> points = service.intraday(today, PARIS);

        assertThat(points)
                .containsExactly(
                        new IntradayPoint(today.atStartOfDay(PARIS).toInstant(), new BigDecimal("108000")),
                        new IntradayPoint(now, new BigDecimal("110000")));
    }

    @Test
    void returnsOpeningRecordedAndNowPointsForToday() {
        LocalDate today = LocalDate.of(2026, 9, 24);
        Instant recordedAt = today.atStartOfDay(PARIS).plusHours(10).toInstant();
        Instant now = today.atStartOfDay(PARIS).plusHours(14).toInstant();
        Clock clock = Clock.fixed(now, PARIS);
        LoadValuationsPort loadValuations =
                (from, to) -> List.of(new IntradayValuation(recordedAt, new BigDecimal("109200")));
        IntradayHistoryService service = new IntradayHistoryService(() -> PORTFOLIO, loadValuations, clock);

        List<IntradayPoint> points = service.intraday(today, PARIS);

        assertThat(points)
                .containsExactly(
                        new IntradayPoint(today.atStartOfDay(PARIS).toInstant(), new BigDecimal("108000")),
                        new IntradayPoint(recordedAt, new BigDecimal("109200")),
                        new IntradayPoint(now, new BigDecimal("110000")));
    }

    @Test
    void dropsARecordedPointAheadOfTheClockSoTheSeriesNeverGoesBackward() {
        LocalDate today = LocalDate.of(2026, 9, 24);
        Instant now = today.atStartOfDay(PARIS).plusHours(14).toInstant();
        Instant futureRecordedAt = now.plusSeconds(5);
        Clock clock = Clock.fixed(now, PARIS);
        LoadValuationsPort loadValuations =
                (from, to) -> List.of(new IntradayValuation(futureRecordedAt, new BigDecimal("111000")));
        IntradayHistoryService service = new IntradayHistoryService(() -> PORTFOLIO, loadValuations, clock);

        List<IntradayPoint> points = service.intraday(today, PARIS);

        assertThat(points).isSortedAccordingTo(Comparator.comparing(IntradayPoint::at));
        assertThat(points).last().isEqualTo(new IntradayPoint(now, new BigDecimal("110000")));
    }
}
