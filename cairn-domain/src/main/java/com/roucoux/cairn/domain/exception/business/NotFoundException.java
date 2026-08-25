package com.roucoux.cairn.domain.exception.business;

import java.util.UUID;

public class NotFoundException extends BusinessException {
    public NotFoundException(String what, UUID id) {
        super(what + " " + id + " not found");
    }
}
