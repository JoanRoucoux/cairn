package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.in.CreatePositionUseCase;
import com.roucoux.cairn.domain.port.in.GetPositionUseCase;
import com.roucoux.cairn.domain.port.out.LoadPositionPort;
import com.roucoux.cairn.domain.port.out.MarketDataPort;
import com.roucoux.cairn.domain.port.out.SavePositionPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Use case implementations. Plain Java: wired as beans by the application module. */
public class PositionService implements CreatePositionUseCase, GetPositionUseCase {

    private final SavePositionPort savePositionPort;
    private final LoadPositionPort loadPositionPort;
    private final MarketDataPort marketDataPort;

    public PositionService(
            SavePositionPort savePositionPort, LoadPositionPort loadPositionPort, MarketDataPort marketDataPort) {
        this.savePositionPort = savePositionPort;
        this.loadPositionPort = loadPositionPort;
        this.marketDataPort = marketDataPort;
    }

    @Override
    public Position create(String isin, BigDecimal quantity) {
        BigDecimal price = marketDataPort.currentPrice(isin).orElseThrow(() -> new UnknownInstrumentException(isin));
        return savePositionPort.save(Position.open(isin, quantity, price));
    }

    @Override
    public Optional<Position> byId(UUID id) {
        return loadPositionPort.findById(id);
    }
}
