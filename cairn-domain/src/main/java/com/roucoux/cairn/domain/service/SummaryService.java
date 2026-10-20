package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.CashOnlyAccount;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.model.PeriodBounds;
import com.roucoux.cairn.domain.model.Summary;
import com.roucoux.cairn.domain.model.SummaryPeriod;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.SendDailySummaryUseCase;
import com.roucoux.cairn.domain.port.in.SendPeriodSummaryUseCase;
import com.roucoux.cairn.domain.port.out.SendNotificationPort;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SummaryService implements SendDailySummaryUseCase, SendPeriodSummaryUseCase {

    private static final System.Logger LOG = System.getLogger(SummaryService.class.getName());

    private final GetPerformanceUseCase getPerformance;
    private final GetPortfolioUseCase getPortfolio;
    private final SendNotificationPort sendNotification;
    private final Clock clock;
    private final ZoneId zone;

    public SummaryService(
            GetPerformanceUseCase getPerformance,
            GetPortfolioUseCase getPortfolio,
            SendNotificationPort sendNotification,
            Clock clock,
            ZoneId zone) {
        this.getPerformance = getPerformance;
        this.getPortfolio = getPortfolio;
        this.sendNotification = sendNotification;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public void send() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        sendNotification.send(new Summary(
                SummaryPeriod.DAY,
                today,
                today,
                getPerformance.performance(PerformanceRange.D1),
                cashOnlyAccounts(getPortfolio.get().holdings())));
    }

    @Override
    public void send(SummaryPeriod period) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        PeriodBounds bounds = period.bounds(today);
        Performance performance = getPerformance.performanceSince(bounds.baseline());
        if (performance.from().isAfter(bounds.last())) {
            LOG.log(Level.WARNING, period + " recap not sent: no quote history before " + performance.from());
            return;
        }
        sendNotification.send(new Summary(
                period,
                bounds.first(),
                bounds.last(),
                performance,
                cashOnlyAccounts(getPortfolio.get().holdings())));
    }

    private static List<CashOnlyAccount> cashOnlyAccounts(List<ValuedHolding> holdings) {
        List<ValuedHolding> valued =
                holdings.stream().filter(line -> line.marketValue().isPresent()).toList();
        Set<AccountType> moving = valued.stream()
                .filter(line -> !line.instrument().isPricedAtPar())
                .map(line -> line.account().type())
                .collect(Collectors.toSet());
        Map<Account, Money> byAccount = new LinkedHashMap<>();
        valued.stream()
                .filter(line -> !moving.contains(line.account().type()))
                .forEach(line ->
                        byAccount.merge(line.account(), line.marketValue().orElseThrow(), Money::plus));
        return byAccount.entrySet().stream()
                .map(entry -> new CashOnlyAccount(
                        entry.getKey().name(), entry.getKey().type(), entry.getValue()))
                .sorted(Comparator.comparing(
                                (CashOnlyAccount account) -> account.value().amount())
                        .reversed())
                .toList();
    }
}
