package com.roucoux.cairn.domain.port.in;

import java.math.BigDecimal;
import java.util.UUID;

public interface SetCashBalanceUseCase {

    void setCashBalance(UUID accountId, BigDecimal amount);
}
