package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.AccountBreakdown;

public interface GetAccountAllocationUseCase {

    AccountBreakdown byAccount();
}
