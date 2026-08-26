package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Account;

/** Outbound port: persist a created or updated account. */
public interface SaveAccountPort {

    Account save(Account account);
}
