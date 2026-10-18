package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=a-real-password"
        })
@Testcontainers
class ForwardedHeadersIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void behindTheProxySendsHstsAndASecureCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/session")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "cairn.example.com"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Strict-Transport-Security")).startsWith("max-age=");
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        assertThat(csrf.getSecure()).isTrue();
    }

    @Test
    void withoutTheForwardedHeadersSendsNeitherHstsNorASecureCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/session")).andReturn();

        assertThat(result.getResponse().getHeader("Strict-Transport-Security")).isNull();
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        assertThat(csrf.getSecure()).isFalse();
    }
}
