package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.Allocation;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.generated.model.AllocationResponse;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.PortfolioResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * Maps the domain model to the generated DTOs. One mapper per resource — never a shared one. The
 * domain never rounds; this is the only place where a monetary amount or a ratio is rounded for
 * the wire.
 */
@Component
public class PortfolioRestMapper {

    private static final int AMOUNT_SCALE = 2;
    private static final int RATIO_SCALE = 6;

    private final Clock clock;

    public PortfolioRestMapper(Clock clock) {
        this.clock = clock;
    }

    public PortfolioResponse toResponse(Portfolio portfolio) {
        PortfolioResponse response = new PortfolioResponse();
        response.setTotalEur(amount(portfolio.total()));
        response.setDayChangeEur(amount(portfolio.dayChange()));
        response.setDayChangeRatio(ratio(portfolio.dayChange().amount(), previousTotal(portfolio)));
        portfolio.unrealizedGain().ifPresent(gain -> {
            response.setUnrealizedGainEur(amount(gain));
            response.setUnrealizedGainRatio(ratio(gain.amount(), costBasisTotal(portfolio, gain)));
        });
        response.setStaleCount(portfolio.staleCount());
        response.setGeneratedAt(OffsetDateTime.now(clock));
        response.setByAssetClass(
                portfolio.byAssetClass().stream().map(this::toAllocation).toList());
        response.setByAccount(
                portfolio.byAccount().stream().map(this::toAllocation).toList());
        response.setHoldings(portfolio.holdings().stream().map(this::toHolding).toList());
        return response;
    }

    private AllocationResponse toAllocation(Allocation allocation) {
        AllocationResponse response = new AllocationResponse();
        response.setLabel(allocation.label());
        response.setValueEur(amount(allocation.value()));
        response.setShare(allocation.share().setScale(RATIO_SCALE, RoundingMode.HALF_UP));
        return response;
    }

    private HoldingResponse toHolding(ValuedHolding line) {
        Holding holding = line.holding();
        Instrument instrument = line.instrument();
        Account account = line.account();

        HoldingResponse response = new HoldingResponse();
        response.setId(holding.id());
        response.setAccountId(account.id());
        response.setAccountName(account.name());
        response.setAccountType(
                HoldingResponse.AccountTypeEnum.valueOf(account.type().name()));
        response.setInstrumentId(instrument.id());
        response.setInstrumentName(instrument.name());
        response.setIsin(instrument.isin());
        response.setAssetClass(
                HoldingResponse.AssetClassEnum.valueOf(instrument.assetClass().name()));
        response.setQuantity(holding.quantity());
        holding.costBasis().ifPresent(cost -> response.setAverageCost(scaledAmount(cost)));
        response.setPrice(scaledAmount(line.quote().price()));
        response.setPriceCurrency(line.quote().currency());
        response.setPriceAsOf(line.quote().asOf());
        response.setPriceSource(
                HoldingResponse.PriceSourceEnum.valueOf(instrument.priceSource().name()));
        response.setStale(line.isStale(clock));
        response.setMarketValueEur(amount(line.marketValue()));
        line.unrealizedGain().ifPresent(gain -> response.setUnrealizedGainEur(amount(gain)));
        line.unrealizedGainRatio().ifPresent(value -> response.setUnrealizedGainRatio(scaledRatio(value)));
        line.dayChange().ifPresent(change -> response.setDayChangeEur(amount(change)));
        line.dayChangeRatio().ifPresent(value -> response.setDayChangeRatio(scaledRatio(value)));
        return response;
    }

    private static BigDecimal previousTotal(Portfolio portfolio) {
        return portfolio.total().amount().subtract(portfolio.dayChange().amount());
    }

    private static BigDecimal costBasisTotal(Portfolio portfolio, Money unrealizedGain) {
        return portfolio.total().amount().subtract(unrealizedGain.amount());
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() == 0 ? null : numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(Money money) {
        return scaledAmount(money.amount());
    }

    private static BigDecimal scaledAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaledRatio(BigDecimal ratio) {
        return ratio.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
