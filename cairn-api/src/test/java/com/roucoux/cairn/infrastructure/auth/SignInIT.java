package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
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

    private static final String CLIENT = "127.0.0.1";

    private static final MutableClock THROTTLE_CLOCK = new MutableClock();

    @TestConfiguration(proxyBeanMethods = false)
    static class FrozenThrottleConfig {

        @Bean
        @Primary
        PasswordGuessThrottle frozenThrottle() {
            return new PasswordGuessThrottle(THROTTLE_CLOCK);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordGuessThrottle throttle;

    @AfterEach
    void forgetFailures() {
        throttle.recordSuccess(CLIENT);
    }

    private ResultActions signInWith(String password) throws Exception {
        return mockMvc.perform(post("/authenticate")
                .param("username", "alex")
                .param("password", password)
                .with(csrf()));
    }

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

        assertThat(output).contains("authentication failed for alex from 127.0.0.1: BadCredentialsException");
        assertThat(output).doesNotContain("at org.springframework.security");
    }

    @Test
    void aSuccessfulSignInResetsTheCountOfTheAddress() throws Exception {
        for (int i = 0; i < PasswordGuessThrottle.FREE_ATTEMPTS - 1; i++) {
            signInWith("wrong").andExpect(status().isUnauthorized());
        }
        signInWith("a-real-password").andExpect(status().isNoContent());

        for (int i = 0; i < PasswordGuessThrottle.FREE_ATTEMPTS - 1; i++) {
            signInWith("wrong").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void refusesWith429AndRetryAfterAfterFiveAttemptsFromOneAddress() throws Exception {
        for (int i = 0; i < PasswordGuessThrottle.FREE_ATTEMPTS; i++) {
            signInWith("wrong").andExpect(status().isUnauthorized());
        }

        MvcResult refused = signInWith("a-real-password")
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(refused.getResponse().getHeader("Retry-After")).isEqualTo("1");
        THROTTLE_CLOCK.advance(Duration.ofSeconds(1));
        signInWith("a-real-password").andExpect(status().isNoContent());
    }

    @Test
    void neverThrottlesTheStartOfAPasskeySignIn() throws Exception {
        for (int i = 0; i < PasswordGuessThrottle.FREE_ATTEMPTS; i++) {
            throttle.admit(CLIENT);
        }

        mockMvc.perform(post("/webauthn/authenticate/options").with(csrf())).andExpect(status().isOk());
    }

    @Test
    void servesNoSignOutConfirmationPage() throws Exception {
        mockMvc.perform(get("/logout")).andExpect(status().isUnauthorized());
    }
}
