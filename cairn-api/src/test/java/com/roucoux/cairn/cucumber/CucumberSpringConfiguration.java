package com.roucoux.cairn.cucumber;

import com.roucoux.cairn.domain.port.out.PublishEventPort;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.permit-all=true",
            "app.security.password=test-password"
        })
@Testcontainers
public class CucumberSpringConfiguration {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @MockitoBean
    PublishEventPort publishEvent;
}
