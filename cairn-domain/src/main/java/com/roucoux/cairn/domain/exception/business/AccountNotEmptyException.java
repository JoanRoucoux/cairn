package com.roucoux.cairn.domain.exception.business;

import java.util.UUID;

public class AccountNotEmptyException extends BusinessException {

    public AccountNotEmptyException(UUID accountId, long holdingCount) {
        super("account " + accountId + " still holds " + holdingCount
                + " holding(s): sell or delete them before deleting the account");
    }
}
