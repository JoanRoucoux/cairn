package com.roucoux.cairn.domain.exception.technical;

public class MarketDataUnavailableException extends TechnicalException {

    public MarketDataUnavailableException(String isin, Throwable cause) {
        super("Market data unavailable for instrument: " + isin, cause);
    }

    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
