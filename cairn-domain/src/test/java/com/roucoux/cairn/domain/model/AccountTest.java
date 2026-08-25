package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void exposesItsFields() {
        UUID id = UUID.randomUUID();
        Account account = new Account(id, "Compte-titres", AccountType.CTO, "Boursorama");

        assertThat(account.id()).isEqualTo(id);
        assertThat(account.name()).isEqualTo("Compte-titres");
        assertThat(account.type()).isEqualTo(AccountType.CTO);
        assertThat(account.institution()).isEqualTo("Boursorama");
    }
}
