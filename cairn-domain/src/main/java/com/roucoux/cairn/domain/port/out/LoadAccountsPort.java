package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: read access to stored accounts. */
public interface LoadAccountsPort {

    List<Account> findAll();

    Optional<Account> findById(UUID id);
}
