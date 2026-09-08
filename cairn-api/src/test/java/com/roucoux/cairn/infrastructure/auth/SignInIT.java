package com.roucoux.cairn.infrastructure.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The status codes below are decided by the filter chain and nowhere else, so this boots the real
 * one. MockMvc rather than TestRestTemplate, for the csrf() post-processor: obtaining a token over
 * real HTTP would test the cookie filter instead of the sign-in it is meant to exercise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=a-real-password"
        })
@Testcontainers
class SignInIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void answersNoContentOnTheRightPassword() throws Exception {
        mockMvc.perform(post("/authenticate")
                        .param("username", "joan")
                        .param("password", "a-real-password")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void answersUnauthorizedRatherThanRedirectingOnAWrongPassword() throws Exception {
        mockMvc.perform(post("/authenticate")
                        .param("username", "joan")
                        .param("password", "wrong")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void servesNoSignOutConfirmationPage() throws Exception {
        mockMvc.perform(get("/logout")).andExpect(status().isUnauthorized());
    }
}
