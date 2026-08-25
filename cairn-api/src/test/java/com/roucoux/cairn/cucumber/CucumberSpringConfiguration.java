package com.roucoux.cairn.cucumber;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Spring context for every scenario in this glue package (cucumber-spring wires it once
 * per run). Boots the full application against a real PostgreSQL, migrated with the schema
 * module's real changelog — same setup as {@link com.roucoux.cairn.ApplicationIT}, since
 * booting the context needs a schema to validate against regardless of which scenario runs.
 * Security is opened up (no IdP involved): these scenarios test business behavior, not
 * authentication, which is already covered by the unit tests and ApplicationIT.
 *
 * <p>Cucumber requires glue classes to be public, unlike the rest of this test suite.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.permit-all=true"
        })
@Testcontainers
public class CucumberSpringConfiguration {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    /** Stands in for the market data provider; step definitions stub it per scenario. */
    static final WireMockServer MARKET_DATA =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void marketDataBaseUrl(DynamicPropertyRegistry registry) {
        if (!MARKET_DATA.isRunning()) {
            MARKET_DATA.start();
        }
        registry.add("app.client.market-data.base-url", MARKET_DATA::baseUrl);
    }
}
