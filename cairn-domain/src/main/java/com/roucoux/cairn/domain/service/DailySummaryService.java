package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.DailySummary;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.in.SendDailySummaryUseCase;
import com.roucoux.cairn.domain.port.out.SendNotificationPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public class DailySummaryService implements SendDailySummaryUseCase {

    private final GetPerformanceUseCase getPerformance;
    private final SendNotificationPort sendNotification;
    private final Clock clock;
    private final ZoneId zone;

    public DailySummaryService(
            GetPerformanceUseCase getPerformance, SendNotificationPort sendNotification, Clock clock, ZoneId zone) {
        this.getPerformance = getPerformance;
        this.sendNotification = sendNotification;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public void send() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        sendNotification.send(new DailySummary(today, getPerformance.performance(PerformanceRange.D1)));
    }
}
