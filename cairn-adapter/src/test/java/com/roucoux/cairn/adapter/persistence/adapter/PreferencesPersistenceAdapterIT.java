package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.PreferencesJpaRepository;
import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;
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
@Import(PreferencesPersistenceAdapter.class)
class PreferencesPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    PreferencesPersistenceAdapter preferences;

    @Autowired
    PreferencesJpaRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seedTheSingleRow() {
        jdbc.update("insert into preferences (id, language, theme, updated_at) values (1, 'fr', 'system', now())");
    }

    @Test
    void savesAndLoadsBothEnumsAsLowercaseText() {
        preferences.save(new Preferences(Language.EN, Theme.LIGHT));
        repository.flush();

        assertThat(jdbc.queryForObject("select language from preferences", String.class))
                .isEqualTo("en");
        assertThat(jdbc.queryForObject("select theme from preferences", String.class))
                .isEqualTo("light");
        assertThat(preferences.load()).isEqualTo(new Preferences(Language.EN, Theme.LIGHT));
    }
}
