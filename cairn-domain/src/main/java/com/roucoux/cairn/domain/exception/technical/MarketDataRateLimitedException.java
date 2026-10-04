package com.roucoux.cairn.domain.exception.technical;

public class MarketDataRateLimitedException extends MarketDataUnavailableException {

    public MarketDataRateLimitedException(String message) {
        super(message);
    }
}
