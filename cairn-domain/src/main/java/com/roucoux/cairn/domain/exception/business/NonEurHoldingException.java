package com.roucoux.cairn.domain.exception.business;

/**
 * Thrown when a holding's instrument is priced in a currency other than EUR. Cairn is EUR-only in
 * v1 (see the design spec's non-objectives): rather than crash the whole portfolio on an unmapped
 * currency mismatch, this identifies the offending instrument up front.
 */
public class NonEurHoldingException extends BusinessException {

    public NonEurHoldingException(String isin, String currency) {
        super("Instrument " + isin + " is priced in " + currency + ", but only EUR is supported");
    }
}
