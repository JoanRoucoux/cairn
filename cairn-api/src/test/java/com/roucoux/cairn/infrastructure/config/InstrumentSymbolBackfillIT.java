package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "spring.liquibase.contexts=demo",
            "app.security.password=test-password"
        })
@Testcontainers
class InstrumentSymbolBackfillIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void copiesTheSourceReferenceOfYahooInstrumentsAndLeavesTheOthersEmpty() {
        Map<String, String> symbolByName = jdbc.query("select name, symbol from instruments", rows -> {
            Map<String, String> result = new HashMap<>();
            while (rows.next()) {
                result.put(rows.getString("name"), rows.getString("symbol"));
            }
            return result;
        });

        assertThat(symbolByName)
                .containsEntry("Global Growth Tracker", "GGT.PA")
                .containsEntry("Northwind Traders", "NWT.PA")
                .containsEntry("Bitcoin", null)
                .containsEntry("Livret A", null);
    }
}
