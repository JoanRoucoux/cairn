package com.roucoux.cairn.domain.exception.business;

public class UnknownInstrumentException extends BusinessException {

    public UnknownInstrumentException(String isin) {
        super("Unknown instrument: " + isin);
    }
}
