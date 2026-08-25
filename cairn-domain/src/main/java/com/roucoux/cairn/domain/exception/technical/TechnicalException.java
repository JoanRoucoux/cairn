package com.roucoux.cairn.domain.exception.technical;

/**
 * Base type for technical failures of an outbound dependency the domain relies on (an adapter could
 * not fulfil a port). The api layer maps every {@code TechnicalException} to a 5xx problem detail.
 */
public abstract class TechnicalException extends RuntimeException {

    protected TechnicalException(String message) {
        super(message);
    }

    protected TechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}
