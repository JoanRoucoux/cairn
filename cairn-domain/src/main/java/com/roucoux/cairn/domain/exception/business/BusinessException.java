package com.roucoux.cairn.domain.exception.business;

/**
 * Base type for domain business-rule violations (a request the domain refuses on functional
 * grounds). The api layer maps every {@code BusinessException} to a 4xx problem detail.
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }
}
