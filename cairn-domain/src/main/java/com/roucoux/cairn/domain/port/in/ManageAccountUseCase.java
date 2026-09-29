package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import java.util.UUID;

public interface ManageAccountUseCase {

    Account create(String name, AccountType type, String institution);

    Account update(UUID id, String name, AccountType type, String institution);

    void delete(UUID id);
}
