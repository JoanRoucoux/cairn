package com.roucoux.cairn.domain.exception.technical;

/**
 * Thrown when the market data provider cannot be reached or answers with an error. The
 * {@link com.roucoux.cairn.domain.port.out.MarketDataPort} lives in the domain, so its failure
 * contract does too; the outbound adapter raises it. Mapped to 502 by the api layer.
 */
public class MarketDataUnavailableException extends TechnicalException {

    public MarketDataUnavailableException(String isin, Throwable cause) {
        super("Market data unavailable for instrument: " + isin, cause);
    }
}
