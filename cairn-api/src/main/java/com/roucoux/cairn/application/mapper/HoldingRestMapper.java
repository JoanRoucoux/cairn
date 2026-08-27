package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.generated.model.HoldingResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link Holding} (bare, or joined into a {@link ValuedHolding}) to the generated DTO. The
 * domain never rounds; this is the only place where a monetary amount or a ratio is rounded for
 * the wire.
 */
@Component
public class HoldingRestMapper {

    private static final int AMOUNT_SCALE = 2;
    private static final int RATIO_SCALE = 6;

    private final Clock clock;

    public HoldingRestMapper(Clock clock) {
        this.clock = clock;
    }

    /**
     * Bare-holding fallback, used only when no {@link ValuedHolding} could be resolved (e.g. a
     * brand-new instrument with no quote fetched yet). The market-data fields are then genuinely
     * unpriceable and stay absent from the JSON — never coerced to 0.
     */
    public HoldingResponse toResponse(Holding holding) {
        HoldingResponse response = new HoldingResponse();
        response.setId(holding.id());
        response.setAccountId(holding.accountId());
        response.setInstrumentId(holding.instrumentId());
        response.setQuantity(holding.quantity());
        holding.costBasis().ifPresent(cost -> response.setAverageCost(scaledAmount(cost)));
        return response;
    }

    public HoldingResponse toResponse(ValuedHolding line) {
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
