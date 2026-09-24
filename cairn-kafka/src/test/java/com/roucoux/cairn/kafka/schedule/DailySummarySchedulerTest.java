package com.roucoux.cairn.kafka.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.port.in.SendDailySummaryUseCase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.support.CronExpression;

class DailySummarySchedulerTest {

    private static final ZoneId PARIS = ZoneId.of(DailySummaryScheduler.ZONE);

    private final SendDailySummaryUseCase sendDailySummary = mock(SendDailySummaryUseCase.class);
    private final Heartbeat heartbeat = mock(Heartbeat.class);
    private final DailySummaryScheduler scheduler = new DailySummaryScheduler(sendDailySummary, heartbeat);

    @Test
    void sendsTheSummaryThenPingsTheHeartbeatOnSuccess() {
        scheduler.sendDailySummary();

        verify(sendDailySummary).send();
        verify(heartbeat).ping();
    }

    @Test
    void aFailedSendIsLoggedNeverPingsTheHeartbeatAndNeverPropagates() {
        Mockito.doThrow(new NotificationDeliveryException("Telegram call failed"))
                .when(sendDailySummary)
                .send();

        scheduler.sendDailySummary();

        verify(heartbeat, never()).ping();
    }

    @Test
    void cronFiresAtNineteenFortyFiveOnWeekdaysOnly() {
        CronExpression cron = CronExpression.parse(DailySummaryScheduler.CRON);
        ZonedDateTime fridayNineteenThirty = ZonedDateTime.of(LocalDate.of(2026, 9, 25), LocalTime.of(19, 30), PARIS);

        ZonedDateTime next = cron.next(fridayNineteenThirty);
        ZonedDateTime afterThat = cron.next(next);

        assertThat(next.toLocalTime()).isEqualTo(LocalTime.of(19, 45));
        assertThat(next.getDayOfWeek().toString()).isEqualTo("FRIDAY");
        assertThat(afterThat.getDayOfWeek().toString()).isEqualTo("MONDAY");
    }
}
