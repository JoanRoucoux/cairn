package com.roucoux.cairn.kafka.schedule;

import com.roucoux.cairn.domain.model.SummaryPeriod;
import com.roucoux.cairn.domain.port.in.SendPeriodSummaryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PeriodSummaryScheduler {

    static final String WEEKLY_CRON = "0 0 9 * * SAT";
    static final String MONTHLY_CRON = "0 0 9 1 * *";
    static final String YEARLY_CRON = "0 5 9 1 1 *";
    static final String ZONE = "Europe/Paris";

    private static final Logger log = LoggerFactory.getLogger(PeriodSummaryScheduler.class);

    private final SendPeriodSummaryUseCase sendPeriodSummary;

    PeriodSummaryScheduler(SendPeriodSummaryUseCase sendPeriodSummary) {
        this.sendPeriodSummary = sendPeriodSummary;
    }

    @Scheduled(cron = WEEKLY_CRON, zone = ZONE)
    void sendWeeklySummary() {
        send(SummaryPeriod.WEEK, "Weekly");
    }

    @Scheduled(cron = MONTHLY_CRON, zone = ZONE)
    void sendMonthlySummary() {
        send(SummaryPeriod.MONTH, "Monthly");
    }

    @Scheduled(cron = YEARLY_CRON, zone = ZONE)
    void sendYearlySummary() {
        send(SummaryPeriod.YEAR, "Yearly");
    }

    private void send(SummaryPeriod period, String label) {
        try {
            sendPeriodSummary.send(period);
        } catch (RuntimeException e) {
            log.error("{} summary send failed", label, e);
        }
    }
}
