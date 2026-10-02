package com.roucoux.cairn.domain.exception.business;

import java.util.UUID;

public class InstrumentAlreadyHeldException extends DuplicateHoldingException {
    public InstrumentAlreadyHeldException(UUID accountId, UUID instrumentId) {
        super(accountId, instrumentId);
    }
}
