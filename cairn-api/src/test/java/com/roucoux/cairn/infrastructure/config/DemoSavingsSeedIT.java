package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
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
class DemoSavingsSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theDemoSavingsAccountsEachHoldOnlyTheEuroCashBalance() {
        List<Map<String, Object>> lines = jdbc.queryForList("""
                select a.name as account, a.type, a.institution, i.name as instrument, i.source_ref, h.quantity
                  from holdings h
                  join accounts a on a.id = h.account_id
                  join instruments i on i.id = h.instrument_id
                 where a.type = 'SAVINGS'
                 order by a.name
                """);

        assertThat(lines).hasSize(2);
        assertThat(lines)
                .extracting(line -> line.get("account"), line -> line.get("institution"))
                .containsExactly(tuple("LDDS", "Fortuneo"), tuple("Livret A", "Fortuneo"));
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.get("instrument")).isEqualTo("Euros");
            assertThat(line.get("source_ref")).isEqualTo("EUR");
        });
        assertThat((BigDecimal) lines.get(1).get("quantity")).isEqualByComparingTo("1000");
    }

    @Test
    void theOrphanBookletInstrumentIsGone() {
        assertThat(jdbc.queryForObject("select count(*) from instruments where name = 'Livret A'", Integer.class))
                .isZero();
    }
}
