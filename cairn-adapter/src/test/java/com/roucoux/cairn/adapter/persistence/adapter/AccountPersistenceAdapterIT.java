package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.AccountJpaRepository;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.hikari.connection-timeout=250"})
@Import(AccountPersistenceAdapter.class)
class AccountPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    AccountPersistenceAdapter accounts;

    @Autowired
    AccountJpaRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void anUpdateKeepsTheOriginalCreationInstant() {
        UUID id = UUID.randomUUID();
        accounts.save(new Account(id, "Northwind PEA", AccountType.PEA, "Contoso Bank"));
        repository.flush();
        Timestamp original =
                Timestamp.from(Instant.now().minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MILLIS));
        jdbc.update("update accounts set created_at = ? where id = ?", original, id);

        accounts.save(new Account(id, "Northwind PEA renamed", AccountType.PEA, "Contoso Bank"));
        repository.flush();

        assertThat(jdbc.queryForObject("select created_at from accounts where id = ?", Timestamp.class, id))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject("select name from accounts where id = ?", String.class, id))
                .isEqualTo("Northwind PEA renamed");
    }
}
