package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(nullable = false, length = 80)
    private String institution;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountEntity() {}

    public static AccountEntity fromDomain(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.id = account.id();
        entity.name = account.name();
        entity.type = account.type();
        entity.institution = account.institution();
        entity.createdAt = Instant.now();
        return entity;
    }

    public Account toDomain() {
        return new Account(id, name, type, institution);
    }
}
