package com.roucoux.cairn.domain.exception.business;

/** Thrown when a holding is given no quantity at all, which is a deletion rather than a change (mapped to 422). */
public class ZeroQuantityException extends BusinessException {

    public ZeroQuantityException() {
        super("quantity must not be zero: delete the holding instead");
    }
}
