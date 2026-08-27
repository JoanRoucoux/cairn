package com.roucoux.cairn.domain.exception.technical;

/**
 * Thrown when a market data provider (Yahoo, CoinGecko, SG Sirius) cannot be reached or answers
 * with an error. Its failure contract lives in the domain alongside the outbound quote ports; the
 * outbound adapters raise it. Mapped to 502 by the api layer.
 */
public class MarketDataUnavailableException extends TechnicalException {

    public MarketDataUnavailableException(String isin, Throwable cause) {
        super("Market data unavailable for instrument: " + isin, cause);
    }

    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
