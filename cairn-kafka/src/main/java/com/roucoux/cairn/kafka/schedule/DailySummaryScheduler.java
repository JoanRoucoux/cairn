package com.roucoux.cairn.kafka.schedule;

import com.roucoux.cairn.domain.port.in.SendDailySummaryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DailySummaryScheduler {

    static final String CRON = "0 45 19 * * MON-FRI";
    static final String ZONE = "Europe/Paris";

    private static final Logger log = LoggerFactory.getLogger(DailySummaryScheduler.class);

    private final SendDailySummaryUseCase sendDailySummary;
    private final Heartbeat heartbeat;

    DailySummaryScheduler(
            SendDailySummaryUseCase sendDailySummary, @Qualifier("summaryHeartbeat") Heartbeat heartbeat) {
        this.sendDailySummary = sendDailySummary;
        this.heartbeat = heartbeat;
    }

    @Scheduled(cron = CRON, zone = ZONE)
    void sendDailySummary() {
        try {
            sendDailySummary.send();
            heartbeat.ping();
        } catch (RuntimeException e) {
            log.error("Daily summary send failed", e);
        }
    }
}
