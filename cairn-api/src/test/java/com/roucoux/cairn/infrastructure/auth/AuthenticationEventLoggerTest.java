package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(OutputCaptureExtension.class)
class AuthenticationEventLoggerTest {

    private final AuthenticationEventLogger logger = new AuthenticationEventLogger();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void truncatesTheSuppliedUsernameToThirtyTwoCharacters() {
        String username = "a".repeat(50);

        assertThat(AuthenticationEventLogger.truncated(username)).isEqualTo("a".repeat(32));
        assertThat(AuthenticationEventLogger.truncated("alex")).isEqualTo("alex");
        assertThat(AuthenticationEventLogger.truncated(null)).isEmpty();
    }

    @Test
    void logsTheClientAddressWithAFailedSignIn(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        logger.onFailure(failure("a".repeat(50)));

        assertThat(output).contains("authentication failed for " + "a".repeat(32) + " from 203.0.113.7:");
        assertThat(output).doesNotContain("a".repeat(33));
    }

    @Test
    void logsAnUnknownAddressOutsideARequest(CapturedOutput output) {
        logger.onFailure(failure("alex"));

        assertThat(output).contains("authentication failed for alex from unknown: BadCredentialsException");
    }

    private static AuthenticationFailureBadCredentialsEvent failure(String username) {
        return new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken(username, "x"), new BadCredentialsException("bad"));
    }
}
