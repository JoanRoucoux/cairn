package com.roucoux.cairn.domain.exception.business;

public class ZeroQuantityException extends BusinessException {

    public ZeroQuantityException() {
        super("quantity must not be zero: delete the holding instead");
    }
}
