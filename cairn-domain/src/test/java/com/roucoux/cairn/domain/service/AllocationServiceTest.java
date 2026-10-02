package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountAllocation;
import com.roucoux.cairn.domain.model.AccountBreakdown;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.Allocation;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.AssetClassAllocation;
import com.roucoux.cairn.domain.model.AssetClassBreakdown;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllocationServiceTest {

    private static final Account PEA = new Account(UUID.randomUUID(), "Saxo", AccountType.PEA, "Saxo Bank");
    private static final Account SAVINGS = new Account(UUID.randomUUID(), "Fortuneo", AccountType.SAVINGS, "Fortuneo");

    private final ValuedHolding etfA = line(PEA, AssetClass.ETF, "10", "60.00");
    private final ValuedHolding etfB = line(SAVINGS, AssetClass.ETF, "5", "40.00");
    private final ValuedHolding cash = line(SAVINGS, AssetClass.CASH, "200", "1");
    private final ValuedHolding unvalued = unvaluedLine(PEA, AssetClass.ETF);
    private final ValuedHolding usd = usdLine(PEA, AssetClass.ETF);

    private final Portfolio portfolio = new Portfolio(
            Money.eur(new BigDecimal("1000")),
            Money.zeroEur(),
            Optional.empty(),
            List.of(
                    new Allocation("ETF", Money.eur(new BigDecimal("800")), new BigDecimal("0.8")),
                    new Allocation("CASH", Money.eur(new BigDecimal("200")), new BigDecimal("0.2"))),
            List.of(
                    new Allocation("Saxo", Money.eur(new BigDecimal("600")), new BigDecimal("0.6")),
                    new Allocation("Fortuneo", Money.eur(new BigDecimal("400")), new BigDecimal("0.4"))),
            List.of(etfA, etfB, cash, unvalued, usd),
            0,
            1,
            1);

    private final AllocationService service = new AllocationService(() -> portfolio);

    @Test
    void keepsTheClassOrderValuesAndSharesOfThePortfolio() {
        AssetClassBreakdown breakdown = service.byAssetClass();

        assertThat(breakdown.total()).isEqualTo(portfolio.total());
        assertThat(breakdown.items())
                .extracting(AssetClassAllocation::assetClass, AssetClassAllocation::value, AssetClassAllocation::share)
                .containsExactly(
                        tuple(AssetClass.ETF, Money.eur(new BigDecimal("800")), new BigDecimal("0.8")),
                        tuple(AssetClass.CASH, Money.eur(new BigDecimal("200")), new BigDecimal("0.2")));
    }

    @Test
    void countsEveryLineOfEachClassExcludedOnesIncluded() {
        assertThat(service.byAssetClass().items())
                .extracting(AssetClassAllocation::lineCount)
                .containsExactly(4, 1);
    }

    @Test
    void carriesTheExcludedLineCountsOnBothBreakdowns() {
        assertThat(service.byAssetClass().unvaluedCount()).isEqualTo(1);
        assertThat(service.byAssetClass().nonEurCount()).isEqualTo(1);
        assertThat(service.byAccount().unvaluedCount()).isEqualTo(1);
        assertThat(service.byAccount().nonEurCount()).isEqualTo(1);
    }

    @Test
    void keepsTheAccountOrderValuesAndSharesAndCarriesTheAccount() {
        AccountBreakdown breakdown = service.byAccount();

        assertThat(breakdown.total()).isEqualTo(portfolio.total());
        assertThat(breakdown.items())
                .extracting(AccountAllocation::account, AccountAllocation::value, AccountAllocation::share)
                .containsExactly(
                        tuple(PEA, Money.eur(new BigDecimal("600")), new BigDecimal("0.6")),
                        tuple(SAVINGS, Money.eur(new BigDecimal("400")), new BigDecimal("0.4")));
    }

    @Test
    void countsEveryLineOfEachAccountExcludedOnesIncluded() {
        assertThat(service.byAccount().items())
                .extracting(AccountAllocation::lineCount)
                .containsExactly(3, 2);
    }

    private static ValuedHolding line(Account account, AssetClass assetClass, String quantity, String price) {
        Instrument instrument = instrument(assetClass);
        Holding holding = new Holding(
                UUID.randomUUID(), account.id(), instrument.id(), new BigDecimal(quantity), null, Instant.EPOCH);
        Quote quote = new Quote(
                instrument.id(),
                LocalDate.of(2026, 8, 21),
                new BigDecimal(price),
                "EUR",
                instrument.priceSource(),
                Instant.parse("2026-08-21T18:00:00Z"));
        return new ValuedHolding(holding, instrument, account, Optional.of(quote), Optional.empty());
    }

    private static ValuedHolding unvaluedLine(Account account, AssetClass assetClass) {
        Instrument instrument = instrument(assetClass);
        Holding holding =
                new Holding(UUID.randomUUID(), account.id(), instrument.id(), BigDecimal.ONE, null, Instant.EPOCH);
        return new ValuedHolding(holding, instrument, account, Optional.empty(), Optional.empty());
    }

    private static ValuedHolding usdLine(Account account, AssetClass assetClass) {
        Instrument instrument = instrument(assetClass);
        Holding holding =
                new Holding(UUID.randomUUID(), account.id(), instrument.id(), BigDecimal.ONE, null, Instant.EPOCH);
        Quote quote = new Quote(
                instrument.id(),
                LocalDate.of(2026, 8, 21),
                BigDecimal.TEN,
                "USD",
                instrument.priceSource(),
                Instant.parse("2026-08-21T18:00:00Z"));
        return new ValuedHolding(holding, instrument, account, Optional.of(quote), Optional.empty());
    }

    private static Instrument instrument(AssetClass assetClass) {
        PriceSource source = assetClass == AssetClass.CASH ? PriceSource.MANUAL : PriceSource.YAHOO;
        String ref = source == PriceSource.MANUAL ? null : "TEST.PA";
        return new Instrument(UUID.randomUUID(), "Test", null, "EUR", assetClass, source, ref, null);
    }
}
