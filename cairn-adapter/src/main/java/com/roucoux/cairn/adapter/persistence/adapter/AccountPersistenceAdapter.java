package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.AccountEntity;
import com.roucoux.cairn.adapter.persistence.repository.AccountJpaRepository;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Outbound adapter: implements the domain's read and write ports for accounts with Spring Data JPA. */
@Component
public class AccountPersistenceAdapter implements LoadAccountsPort, SaveAccountPort {

    private final AccountJpaRepository repository;

    public AccountPersistenceAdapter(AccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Account> findAll() {
        return repository.findAll().stream().map(AccountEntity::toDomain).toList();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return repository.findById(id).map(AccountEntity::toDomain);
    }

    @Override
    public Account save(Account account) {
        return repository.save(AccountEntity.fromDomain(account)).toDomain();
    }
}
