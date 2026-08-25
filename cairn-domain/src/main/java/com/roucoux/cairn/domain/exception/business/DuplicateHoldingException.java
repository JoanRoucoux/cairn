package com.roucoux.cairn.domain.exception.business;

import java.util.UUID;

public class DuplicateHoldingException extends BusinessException {
    public DuplicateHoldingException(UUID accountId, UUID instrumentId) {
        super("account " + accountId + " already holds instrument " + instrumentId);
    }
}
