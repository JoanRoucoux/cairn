package com.roucoux.cairn.kafka.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.SummaryPeriod;
import com.roucoux.cairn.domain.port.in.SendPeriodSummaryUseCase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.support.CronExpression;

class PeriodSummarySchedulerTest {

    private static final ZoneId PARIS = ZoneId.of(PeriodSummaryScheduler.ZONE);

    private final SendPeriodSummaryUseCase sendPeriodSummary = mock(SendPeriodSummaryUseCase.class);
    private final PeriodSummaryScheduler scheduler = new PeriodSummaryScheduler(sendPeriodSummary);

    @Test
    void eachScheduledMethodSendsItsOwnPeriod() {
        scheduler.sendWeeklySummary();
        scheduler.sendMonthlySummary();
        scheduler.sendYearlySummary();

        verify(sendPeriodSummary).send(SummaryPeriod.WEEK);
        verify(sendPeriodSummary).send(SummaryPeriod.MONTH);
        verify(sendPeriodSummary).send(SummaryPeriod.YEAR);
    }

    @Test
    void aFailedSendIsLoggedAndNeverPropagates() {
        Mockito.doThrow(new NotificationDeliveryException("Telegram call failed"))
                .when(sendPeriodSummary)
                .send(SummaryPeriod.WEEK);

        assertThatCode(scheduler::sendWeeklySummary).doesNotThrowAnyException();
    }

    @Test
    void weeklyCronFiresOnSaturdayAtNine() {
        CronExpression cron = CronExpression.parse(PeriodSummaryScheduler.WEEKLY_CRON);

        ZonedDateTime next = cron.next(ZonedDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.NOON, PARIS));

        assertThat(next).isEqualTo(ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(9, 0), PARIS));
    }

    @Test
    void monthlyCronFiresOnTheFirstAtNine() {
        CronExpression cron = CronExpression.parse(PeriodSummaryScheduler.MONTHLY_CRON);

        ZonedDateTime next = cron.next(ZonedDateTime.of(LocalDate.of(2026, 10, 20), LocalTime.NOON, PARIS));

        assertThat(next).isEqualTo(ZonedDateTime.of(LocalDate.of(2026, 11, 1), LocalTime.of(9, 0), PARIS));
    }

    @Test
    void yearlyCronFiresOnJanuaryFirstAtFivepast() {
        CronExpression cron = CronExpression.parse(PeriodSummaryScheduler.YEARLY_CRON);

        ZonedDateTime next = cron.next(ZonedDateTime.of(LocalDate.of(2026, 10, 20), LocalTime.NOON, PARIS));

        assertThat(next).isEqualTo(ZonedDateTime.of(LocalDate.of(2027, 1, 1), LocalTime.of(9, 5), PARIS));
    }
}
