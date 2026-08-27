package com.roucoux.cairn.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Spring context for every scenario in this glue package (cucumber-spring wires it once
 * per run). Boots the full application against a real PostgreSQL, migrated with the schema
 * module's real changelog — same setup as {@link com.roucoux.cairn.ApplicationIT}, since booting
 * the context needs a schema to validate against regardless of which scenario runs. Security is
 * opened up ({@code app.security.permit-all}): these scenarios exercise business behavior, not
 * WebAuthn, which is already covered by {@code WebAuthnConfigTest} and {@code ApplicationIT}.
 *
 * <p>No shared external stub server here: these scenarios start from quotes already recorded in
 * the database, they never call a market data source.
 *
 * <p>Cucumber requires glue classes to be public, unlike the rest of this test suite.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.permit-all=true",
            // permit-all only swaps the filter chain; WebAuthnConfig's userDetailsService bean is
            // still built eagerly and refuses the default password outside the local profile.
            "app.security.password=test-password"
        })
@Testcontainers
public class CucumberSpringConfiguration {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
}
