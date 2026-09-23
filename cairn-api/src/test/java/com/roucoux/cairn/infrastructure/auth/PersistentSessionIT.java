package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Modeled on SignInIT: the session cookie's attributes are decided by the filter chain and
 * Spring Session's own JDBC repository, so this boots the real ones against a real PostgreSQL
 * container rather than mocking either. RANDOM_PORT, not the default MOCK web environment: Spring
 * Boot's session auto-configuration only reads server.servlet.session.* on a real embedded server
 * (java -jar), and treats a MOCK web environment's ServletContext, which has no embedded server
 * behind it, as a WAR deployment instead — a branch with different defaults for both the cookie
 * attributes and the timeout. RANDOM_PORT is what production actually runs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=a-real-password"
        })
@Testcontainers
class PersistentSessionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void keepsTheSignedInSessionInPostgresForThirtyDays() throws Exception {
        MvcResult signIn = mockMvc.perform(post("/authenticate")
                        .param("username", "joan")
                        .param("password", "a-real-password")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();

        Integer maxInactive =
                jdbc.queryForObject("select max(max_inactive_interval) from spring_session", Integer.class);
        assertThat(maxInactive).isEqualTo(2_592_000);

        String setCookie = signIn.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith("SESSION="))
                .findFirst()
                .orElseThrow();
        assertThat(setCookie)
                .contains("Max-Age=2592000")
                .contains("Secure")
                .contains("HttpOnly")
                .containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void readsTheSessionBackFromPostgresOnTheNextRequest() throws Exception {
        MvcResult signIn = mockMvc.perform(post("/authenticate")
                        .param("username", "joan")
                        .param("password", "a-real-password")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie session = signIn.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/session").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void removesTheSessionRowOnSignOut() throws Exception {
        MvcResult signIn = mockMvc.perform(post("/authenticate")
                        .param("username", "joan")
                        .param("password", "a-real-password")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie session = signIn.getResponse().getCookie("SESSION");
        Integer before = jdbc.queryForObject("select count(*) from spring_session", Integer.class);

        mockMvc.perform(post("/logout").cookie(session).with(csrf())).andExpect(status().isNoContent());

        Integer after = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
        assertThat(after).isEqualTo(before - 1);
    }
}
