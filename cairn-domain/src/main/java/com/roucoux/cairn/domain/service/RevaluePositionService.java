package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.in.RevaluePositionUseCase;
import com.roucoux.cairn.domain.port.out.MarketDataPort;
import java.math.BigDecimal;

/**
 * Use case implementation. Plain Java: wired as a bean by the batch module — the same domain
 * serves both the API and the batch, which is the point of keeping it framework-free.
 */
public class RevaluePositionService implements RevaluePositionUseCase {

    private final MarketDataPort marketDataPort;

    public RevaluePositionService(MarketDataPort marketDataPort) {
        this.marketDataPort = marketDataPort;
    }

    @Override
    public Position revalue(Position position) {
        BigDecimal price = marketDataPort
                .currentPrice(position.isin())
                .orElseThrow(() -> new UnknownInstrumentException(position.isin()));
        return position.revalue(price);
    }
}
