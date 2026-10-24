package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.TelegramLinkJpaRepository;
import com.roucoux.cairn.domain.model.LinkedChat;
import com.roucoux.cairn.domain.model.PendingLink;
import com.roucoux.cairn.domain.model.TelegramLink;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
@Import(TelegramLinkPersistenceAdapter.class)
class TelegramLinkPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    TelegramLinkPersistenceAdapter links;

    @Autowired
    TelegramLinkJpaRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seedTheSingleRow() {
        jdbc.update("delete from telegram_link");
        jdbc.update("insert into telegram_link (id) values (1)");
    }

    @Test
    void roundTripsALinkedChatAndAPendingToken() {
        Instant linkedAt = Instant.parse("2026-10-24T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-10-24T10:10:00Z");
        String hash = "a".repeat(64);
        TelegramLink link =
                new TelegramLink(new LinkedChat(123456789L, "Alex", linkedAt), new PendingLink(hash, expiresAt));

        links.save(link);
        repository.flush();

        assertThat(links.load()).isEqualTo(link);
        assertThat(jdbc.queryForObject("select chat_id from telegram_link", Long.class))
                .isEqualTo(123456789L);
        assertThat(jdbc.queryForObject("select pending_token_hash from telegram_link", String.class))
                .isEqualTo(hash);
    }

    @Test
    void savingNoneClearsEveryColumn() {
        links.save(new TelegramLink(
                new LinkedChat(1L, "Alex", Instant.parse("2026-10-24T10:00:00Z")),
                new PendingLink("b".repeat(64), Instant.parse("2026-10-24T10:10:00Z"))));
        repository.flush();

        links.save(TelegramLink.NONE);
        repository.flush();

        List<Map<String, Object>> rows = jdbc.queryForList("select * from telegram_link");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("chat_id", null)
                .containsEntry("chat_label", null)
                .containsEntry("linked_at", null)
                .containsEntry("pending_token_hash", null)
                .containsEntry("pending_expires_at", null);
        assertThat(links.load()).isEqualTo(TelegramLink.NONE);
    }
}
