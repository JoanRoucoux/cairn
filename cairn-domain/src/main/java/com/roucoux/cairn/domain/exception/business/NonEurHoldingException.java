package com.roucoux.cairn.domain.exception.business;

public class NonEurHoldingException extends BusinessException {

    public NonEurHoldingException(String isin, String currency) {
        super("Instrument " + isin + " is priced in " + currency + ", but only EUR is supported");
    }
}
