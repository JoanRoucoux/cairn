package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.DailySummary;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.out.SendNotificationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySummaryServiceTest {

    // 23:30 UTC on the 22nd is already the 23rd in Europe/Paris (UTC+2 in September).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T23:30:00Z"), ZoneOffset.UTC);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static final Performance D1_PERFORMANCE = new Performance(
            PerformanceRange.D1,
            LocalDate.of(2026, 9, 22),
            LocalDate.of(2026, 9, 23),
            false,
            Optional.empty(),
            Money.eur(new java.math.BigDecimal("298889")),
            Money.eur(new java.math.BigDecimal("2431")),
            Optional.of(new java.math.BigDecimal("0.0082")),
            List.of());

    @Test
    void readsD1PerformanceAndSendsItOnceForTodayInParis() {
        List<PerformanceRange> requestedRanges = new ArrayList<>();
        GetPerformanceUseCase getPerformance = range -> {
            requestedRanges.add(range);
            return D1_PERFORMANCE;
        };
        List<DailySummary> sent = new ArrayList<>();
        SendNotificationPort sendNotification = sent::add;
        DailySummaryService service = new DailySummaryService(getPerformance, sendNotification, CLOCK, PARIS);

        service.send();

        assertThat(requestedRanges).containsExactly(PerformanceRange.D1);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().date()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(sent.getFirst().performance()).isSameAs(D1_PERFORMANCE);
    }
}
