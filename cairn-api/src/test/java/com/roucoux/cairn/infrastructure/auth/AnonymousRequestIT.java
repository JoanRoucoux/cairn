package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=a-real-password",
            "app.security.username=alex"
        })
@Testcontainers
class AnonymousRequestIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rejectsAnAnonymousRequestWithoutCreatingASession() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from spring_session", Integer.class);

        MvcResult result = mockMvc.perform(get("/session"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(header -> header.startsWith("SESSION="));
        assertThat(result.getRequest().getSession(false)).isNull();
        Integer after = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
        assertThat(after).isEqualTo(before);
    }
}
