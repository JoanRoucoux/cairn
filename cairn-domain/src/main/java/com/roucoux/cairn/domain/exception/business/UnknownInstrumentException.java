package com.roucoux.cairn.domain.exception.business;

/** Thrown when no market data exists for the requested instrument (mapped to 422). */
public class UnknownInstrumentException extends BusinessException {

    public UnknownInstrumentException(String isin) {
        super("Unknown instrument: " + isin);
    }
}
