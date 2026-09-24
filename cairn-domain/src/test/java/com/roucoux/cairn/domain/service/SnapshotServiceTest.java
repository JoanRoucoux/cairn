package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.Snapshot;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.out.SaveSnapshotPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    void datesTheSnapshotAtTheParisDayOfLateEveningRun() {
        Clock clock = Clock.fixed(
                LocalDate.of(2026, 9, 24).atTime(23, 30).atZone(PARIS).toInstant(), PARIS);
        SnapshotService service = serviceWith(clock, List.of(valuedLine(AccountType.CTO, AssetClass.EQUITY, "100")));

        Snapshot snapshot = service.compute();

        assertThat(snapshot.date()).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    @Test
    void datesTheSnapshotAtTheParisDayAcrossMidnight() {
        Clock clock = Clock.fixed(
                LocalDate.of(2026, 9, 25).atTime(0, 30).atZone(PARIS).toInstant(), PARIS);
        SnapshotService service = serviceWith(clock, List.of(valuedLine(AccountType.CTO, AssetClass.EQUITY, "100")));

        Snapshot snapshot = service.compute();

        assertThat(snapshot.date()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void totalsThePortfoliosTotal() {
        SnapshotService service = serviceWith(List.of(
                valuedLine(AccountType.CTO, AssetClass.EQUITY, "100"),
                valuedLine(AccountType.PEA, AssetClass.ETF, "50")));

        Snapshot snapshot = service.compute();

        assertThat(snapshot.totalEur()).isEqualByComparingTo("150");
    }

    @Test
    void splitsByAccountTypeAndAssetClassWithBothSummingToTheTotal() {
        SnapshotService service = serviceWith(List.of(
                valuedLine(AccountType.CTO, AssetClass.EQUITY, "100"),
                valuedLine(AccountType.PEA, AssetClass.ETF, "50"),
                valuedLine(AccountType.CTO, AssetClass.CRYPTO, "25")));

        Snapshot snapshot = service.compute();

        assertThat(snapshot.byAccountType())
                .containsEntry("CTO", new BigDecimal("125"))
                .containsEntry("PEA", new BigDecimal("50"));
        assertThat(snapshot.byAssetClass())
                .containsEntry("EQUITY", new BigDecimal("100"))
                .containsEntry("ETF", new BigDecimal("50"))
                .containsEntry("CRYPTO", new BigDecimal("25"));
        assertThat(sum(snapshot.byAccountType().values())).isEqualByComparingTo(snapshot.totalEur());
        assertThat(sum(snapshot.byAssetClass().values())).isEqualByComparingTo(snapshot.totalEur());
    }

    @Test
    void leavesAnUnvaluedLineOutOfTheVentilationsAndTheTotal() {
        SnapshotService service =
                serviceWith(List.of(valuedLine(AccountType.CTO, AssetClass.EQUITY, "100"), unvaluedLine()));

        Snapshot snapshot = service.compute();

        assertThat(snapshot.totalEur()).isEqualByComparingTo("100");
        assertThat(snapshot.byAssetClass()).containsOnlyKeys("EQUITY");
    }

    @Test
    void handsTheComputedSnapshotToTheSavePort() {
        RecordingSaveSnapshotPort savePort = new RecordingSaveSnapshotPort();
        SnapshotService service =
                serviceWith(fixedClock(), List.of(valuedLine(AccountType.CTO, AssetClass.EQUITY, "100")), savePort);

        Snapshot snapshot = service.compute();

        assertThat(savePort.saved()).containsExactly(snapshot);
    }

    private static Clock fixedClock() {
        return Clock.fixed(
                LocalDate.of(2026, 9, 24).atTime(23, 30).atZone(PARIS).toInstant(), PARIS);
    }

    private static ValuedHolding valuedLine(AccountType accountType, AssetClass assetClass, String marketValue) {
        UUID instrumentId = UUID.randomUUID();
        Account account = new Account(UUID.randomUUID(), "Test", accountType, "Test institution");
        Instrument instrument =
                new Instrument(instrumentId, "Test", null, "EUR", assetClass, PriceSource.YAHOO, "TEST.PA", null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, BigDecimal.ONE, null);
        Quote quote = new Quote(
                instrumentId,
                LocalDate.of(2026, 9, 24),
                new BigDecimal(marketValue),
                "EUR",
                PriceSource.YAHOO,
                fixedClock().instant());
        return new ValuedHolding(holding, instrument, account, Optional.of(quote), Optional.empty());
    }

    private static ValuedHolding unvaluedLine() {
        UUID instrumentId = UUID.randomUUID();
        Account account = new Account(UUID.randomUUID(), "Test", AccountType.CTO, "Test institution");
        Instrument instrument = new Instrument(
                instrumentId, "Test", null, "EUR", AssetClass.EQUITY, PriceSource.YAHOO, "TEST2.PA", null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrumentId, BigDecimal.ONE, null);
        return new ValuedHolding(holding, instrument, account, Optional.empty(), Optional.empty());
    }

    private static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }

    private static SnapshotService serviceWith(List<ValuedHolding> lines) {
        return serviceWith(fixedClock(), lines);
    }

    private static SnapshotService serviceWith(Clock clock, List<ValuedHolding> lines) {
        return serviceWith(clock, lines, new RecordingSaveSnapshotPort());
    }

    private static SnapshotService serviceWith(Clock clock, List<ValuedHolding> lines, SaveSnapshotPort savePort) {
        Money total =
                lines.stream().flatMap(line -> line.marketValue().stream()).reduce(Money.zeroEur(), Money::plus);
        GetPortfolioUseCase getPortfolio = () -> new Portfolio(
                total, Money.zeroEur(), Optional.empty(), List.of(), List.of(), lines, 0, (int) lines.stream()
                        .filter(line -> line.marketValue().isEmpty())
                        .count());
        return new SnapshotService(getPortfolio, savePort, clock);
    }

    private static final class RecordingSaveSnapshotPort implements SaveSnapshotPort {
        private final List<Snapshot> saved = new ArrayList<>();

        @Override
        public void save(Snapshot snapshot) {
            saved.add(snapshot);
        }

        List<Snapshot> saved() {
            return saved;
        }
    }
}
