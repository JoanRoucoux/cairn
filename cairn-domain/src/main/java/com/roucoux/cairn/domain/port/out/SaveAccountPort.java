package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Account;

public interface SaveAccountPort {

    Account save(Account account);
}
