package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SummaryPeriodTest {

    @Test
    void aWeekIsMondayToFridayOfTheWeekContainingYesterday() {
        PeriodBounds bounds = SummaryPeriod.WEEK.bounds(LocalDate.of(2026, 10, 17));

        assertThat(bounds.first()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(bounds.last()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void aWeekCanSpanTwoMonths() {
        PeriodBounds bounds = SummaryPeriod.WEEK.bounds(LocalDate.of(2026, 10, 3));

        assertThat(bounds.first()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(bounds.last()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void aMonthIsThePreviousCalendarMonthIncludingLeapFebruary() {
        PeriodBounds bounds = SummaryPeriod.MONTH.bounds(LocalDate.of(2028, 3, 1));

        assertThat(bounds.first()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(bounds.last()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void onJanuaryFirstTheMonthIsPreviousDecemberAndTheYearIsThePreviousYear() {
        LocalDate newYear = LocalDate.of(2027, 1, 1);

        PeriodBounds month = SummaryPeriod.MONTH.bounds(newYear);
        PeriodBounds year = SummaryPeriod.YEAR.bounds(newYear);

        assertThat(month.first()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(month.last()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(year.first()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(year.last()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void aDayIsTodayOnly() {
        LocalDate today = LocalDate.of(2026, 9, 23);

        PeriodBounds bounds = SummaryPeriod.DAY.bounds(today);

        assertThat(bounds.first()).isEqualTo(today);
        assertThat(bounds.last()).isEqualTo(today);
    }

    @Test
    void theBaselineIsTheDayBeforeTheFirstDay() {
        assertThat(new PeriodBounds(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).baseline())
                .isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
