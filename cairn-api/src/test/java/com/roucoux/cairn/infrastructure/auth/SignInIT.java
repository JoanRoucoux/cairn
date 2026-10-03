package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=a-real-password",
            "app.security.username=alex"
        })
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SignInIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void answersNoContentOnTheRightPassword() throws Exception {
        mockMvc.perform(post("/authenticate")
                        .param("username", "alex")
                        .param("password", "a-real-password")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void answersUnauthorizedRatherThanRedirectingOnAWrongPassword() throws Exception {
        mockMvc.perform(post("/authenticate")
                        .param("username", "alex")
                        .param("password", "wrong")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsASuccessfulSignIn(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/authenticate")
                        .param("username", "alex")
                        .param("password", "a-real-password")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(output).contains("authentication succeeded for alex");
    }

    @Test
    void logsAFailedSignInWithoutStack(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/authenticate")
                        .param("username", "alex")
                        .param("password", "wrong")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(output).contains("authentication failed for alex: BadCredentialsException");
        assertThat(output).doesNotContain("at org.springframework.security");
    }

    @Test
    void servesNoSignOutConfirmationPage() throws Exception {
        mockMvc.perform(get("/logout")).andExpect(status().isUnauthorized());
    }
}
