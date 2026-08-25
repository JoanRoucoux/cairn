package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.GetQuoteUseCase;
import com.roucoux.cairn.domain.port.out.MarketDataPort;

/** Use case implementation. Plain Java: wired as a bean by the application module. */
public class QuoteService implements GetQuoteUseCase {

    private final MarketDataPort marketDataPort;

    public QuoteService(MarketDataPort marketDataPort) {
        this.marketDataPort = marketDataPort;
    }

    @Override
    public Quote byIsin(String isin) {
        return marketDataPort
                .currentPrice(isin)
                .map(price -> new Quote(isin, price))
                .orElseThrow(() -> new UnknownInstrumentException(isin));
    }
}
