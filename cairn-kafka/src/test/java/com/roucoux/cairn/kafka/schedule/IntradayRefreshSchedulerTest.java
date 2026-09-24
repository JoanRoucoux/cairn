package com.roucoux.cairn.kafka.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;

class IntradayRefreshSchedulerTest {

    private static final ZoneId PARIS = ZoneId.of(IntradayRefreshScheduler.ZONE);
    private static final RefreshReport ALL_REFRESHED = new RefreshReport(3, 0, List.of());
    private static final RefreshReport NOTHING_TO_REFRESH = new RefreshReport(0, 0, List.of());
    private static final RefreshReport ALL_FAILED = new RefreshReport(
            0,
            0,
            List.of(new RefreshReport.Failure(UUID.randomUUID(), "Bitcoin", PriceSource.COINGECKO, "unavailable")));

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RefreshQuotesUseCase> refreshQuotesProvider = mock(ObjectProvider.class);

    private final Heartbeat heartbeat = mock(Heartbeat.class);

    private final IntradayRefreshScheduler scheduler = new IntradayRefreshScheduler(refreshQuotesProvider, heartbeat);

    @Test
    void refreshesEquitiesAndEtfsOnAFreshInstanceThenPingsTheHeartbeat() {
        RefreshQuotesUseCase refreshQuotes = mock(RefreshQuotesUseCase.class);
        when(refreshQuotesProvider.getObject()).thenReturn(refreshQuotes);
        when(refreshQuotes.refreshAll(Set.of(AssetClass.EQUITY, AssetClass.ETF), RefreshTrigger.SCHEDULER))
                .thenReturn(ALL_REFRESHED);

        scheduler.refreshEquitiesAndEtfs();

        verify(refreshQuotes).refreshAll(Set.of(AssetClass.EQUITY, AssetClass.ETF), RefreshTrigger.SCHEDULER);
        verify(heartbeat).ping();
    }

    @Test
    void refreshesCryptosOnAFreshInstanceThenPingsTheHeartbeat() {
        RefreshQuotesUseCase refreshQuotes = mock(RefreshQuotesUseCase.class);
        when(refreshQuotesProvider.getObject()).thenReturn(refreshQuotes);
        when(refreshQuotes.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER))
                .thenReturn(ALL_REFRESHED);

        scheduler.refreshCryptos();

        verify(refreshQuotes).refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER);
        verify(heartbeat).ping();
    }

    @Test
    void eachRunAsksTheProviderForANewInstance() {
        RefreshQuotesUseCase first = mock(RefreshQuotesUseCase.class);
        RefreshQuotesUseCase second = mock(RefreshQuotesUseCase.class);
        when(refreshQuotesProvider.getObject()).thenReturn(first, second);
        when(first.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER))
                .thenReturn(ALL_REFRESHED);
        when(second.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER))
                .thenReturn(ALL_REFRESHED);

        scheduler.refreshCryptos();
        scheduler.refreshCryptos();

        verify(first).refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER);
        verify(second).refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER);
        verify(refreshQuotesProvider, times(2)).getObject();
    }

    @Test
    void aFailedRefreshIsLoggedNeverPingsTheHeartbeatAndNeverPropagates() {
        RefreshQuotesUseCase refreshQuotes = mock(RefreshQuotesUseCase.class);
        when(refreshQuotesProvider.getObject()).thenReturn(refreshQuotes);
        when(refreshQuotes.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER))
                .thenThrow(new RuntimeException("provider outage"));

        scheduler.refreshCryptos();

        verify(heartbeat, never()).ping();
    }

    @Test
    void pingsTheHeartbeatWhenThereWasNothingToRefresh() {
        RefreshQuotesUseCase refreshQuotes = mock(RefreshQuotesUseCase.class);
        when(refreshQuotesProvider.getObject()).thenReturn(refreshQuotes);
        when(refreshQuotes.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER))
                .thenReturn(NOTHING_TO_REFRESH);

        scheduler.refreshCryptos();

        verify(heartbeat).ping();
    }

    @Test
    void neverPingsTheHeartbeatWhenEveryInstrumentFailed() {
        RefreshQuotesUseCase refreshQuotes = mock(RefreshQuotesUseCase.class);
        when(refreshQuotesProvider.getObject()).thenReturn(refreshQuotes);
        when(refreshQuotes.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.SCHEDULER))
                .thenReturn(ALL_FAILED);

        scheduler.refreshCryptos();

        verify(heartbeat, never()).ping();
    }

    @Test
    void equityAndEtfCronFiresEveryFifteenMinutesDuringEuronextHoursOnly() {
        CronExpression cron = CronExpression.parse(IntradayRefreshScheduler.EQUITY_ETF_CRON);
        ZonedDateTime mondayEightFortyFive = ZonedDateTime.of(LocalDate.of(2026, 9, 21), LocalTime.of(8, 45), PARIS);

        ZonedDateTime next = cron.next(mondayEightFortyFive);

        assertThat(next.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(next.toLocalTime()).isNotEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void equityAndEtfCronReaches1745ButNever1800() {
        CronExpression cron = CronExpression.parse(IntradayRefreshScheduler.EQUITY_ETF_CRON);
        ZonedDateTime mondayFivePM = ZonedDateTime.of(LocalDate.of(2026, 9, 21), LocalTime.of(17, 30), PARIS);

        ZonedDateTime next = cron.next(mondayFivePM);
        ZonedDateTime afterThat = cron.next(next);

        assertThat(next.toLocalTime()).isEqualTo(LocalTime.of(17, 45));
        assertThat(afterThat.toLocalTime()).isNotEqualTo(LocalTime.of(18, 0));
        assertThat(afterThat.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void equityAndEtfCronNeverFiresOnASaturday() {
        CronExpression cron = CronExpression.parse(IntradayRefreshScheduler.EQUITY_ETF_CRON);
        ZonedDateTime fridayFivePM = ZonedDateTime.of(LocalDate.of(2026, 9, 25), LocalTime.of(17, 45), PARIS);

        ZonedDateTime next = cron.next(fridayFivePM);

        assertThat(next.getDayOfWeek().toString()).isEqualTo("MONDAY");
    }

    @Test
    void cryptoCronFiresEveryFifteenMinutesAroundTheClock() {
        CronExpression cron = CronExpression.parse(IntradayRefreshScheduler.CRYPTO_CRON);
        ZonedDateTime saturdayMidnight = ZonedDateTime.of(LocalDate.of(2026, 9, 26), LocalTime.of(0, 0), PARIS);

        ZonedDateTime next = cron.next(saturdayMidnight);

        assertThat(next.toLocalTime()).isEqualTo(LocalTime.of(0, 15));
    }
}
