package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DemoSavingsSeedOverExistingEurosIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    void reusesTheEurosInstrumentADemoDatabaseAlreadyHolds() throws Exception {
        try (Connection connection =
                DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            Liquibase liquibase = new Liquibase(
                    "db/changelog/changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
            Contexts demo = new Contexts("demo");
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(demo, new LabelExpression());
            int beforeSavingsSeed =
                    pending.stream().map(ChangeSet::getId).toList().indexOf("016-seed-demo-savings");
            liquibase.update(beforeSavingsSeed, "demo");
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.update("""
                    insert into instruments (id, name, currency, asset_class, price_source, source_ref, created_at)
                    values (gen_random_uuid(), 'Euros', 'EUR', 'CASH', 'MANUAL', 'EUR', now())
                    """);

            liquibase.update(demo, new LabelExpression());

            assertThat(jdbc.queryForObject("select count(*) from instruments where source_ref = 'EUR'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                            select count(*) from holdings h join accounts a on a.id = h.account_id
                              join instruments i on i.id = h.instrument_id
                             where a.type = 'SAVINGS' and i.source_ref = 'EUR'
                            """, Integer.class)).isEqualTo(2);
        }
    }
}
