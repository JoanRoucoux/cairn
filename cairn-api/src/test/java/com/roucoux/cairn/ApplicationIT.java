package com.roucoux.cairn;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.port.out.SendNotificationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=test-password"
        })
@Testcontainers
class ApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startsUpAndWiresTheTelegramAdapterWithoutAnyTelegramVariableSet() {
        assertThat(System.getenv("TELEGRAM_BOT_TOKEN")).isNull();
        assertThat(System.getenv("TELEGRAM_CHAT_ID")).isNull();
        assertThat(applicationContext.getBean(SendNotificationPort.class)).isNotNull();
    }

    @Test
    void refusesAnUnauthenticatedApiCall() {
        assertThat(restTemplate.getForEntity("/portfolio", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void leavesTheHealthProbeOpen() {
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void servesNoSignInPageOfItsOwn() {
        assertThat(restTemplate.getForEntity("/login", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handsEveryCallerACsrfTokenToEchoBack() {
        assertThat(restTemplate
                        .getForEntity("/actuator/health", String.class)
                        .getHeaders()
                        .get("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN="));
    }
}
