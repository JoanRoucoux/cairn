package com.roucoux.cairn.domain.exception.business;

/** Thrown when the values an instrument is built with break its own invariants (mapped to 422). */
public class InvalidInstrumentException extends BusinessException {

    public InvalidInstrumentException(String message) {
        super(message);
    }
}
