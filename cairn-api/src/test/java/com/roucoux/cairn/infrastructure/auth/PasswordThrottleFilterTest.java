package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PasswordThrottleFilterTest {

    private final MutableClock clock = new MutableClock();
    private final PasswordGuessThrottle throttle = new PasswordGuessThrottle(clock);
    private final PasswordThrottleFilter filter = new PasswordThrottleFilter(throttle);

    @Test
    void passesAPasswordAttemptThroughWhileNothingIsBlocked() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(attempt("203.0.113.7"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void refusesAPasswordAttemptWithRetryAfterWithoutCallingTheChain() throws Exception {
        exhaust("203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(attempt("203.0.113.7"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void roundsTheRemainingDelayUpToTheNextSecond() throws Exception {
        exhaust("203.0.113.7");
        clock.advance(Duration.ofMillis(1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(attempt("203.0.113.7"), response, new MockFilterChain());

        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void doesNotRefuseAnotherAddress() throws Exception {
        exhaust("203.0.113.7");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(attempt("198.51.100.9"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void neverBlocksAnythingButAPasswordAttempt() throws Exception {
        exhaust("203.0.113.7");
        for (MockHttpServletRequest request : new MockHttpServletRequest[] {
            new MockHttpServletRequest("GET", "/authenticate"),
            new MockHttpServletRequest("POST", "/login/webauthn"),
            new MockHttpServletRequest("POST", "/webauthn/authenticate/options"),
            new MockHttpServletRequest("GET", "/session")
        }) {
            request.setRemoteAddr("203.0.113.7");
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).as(request.getRequestURI()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private void exhaust(String address) {
        for (int i = 0; i < PasswordGuessThrottle.FREE_ATTEMPTS; i++) {
            throttle.admit(address);
        }
    }

    private static MockHttpServletRequest attempt(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/authenticate");
        request.setRemoteAddr(address);
        return request;
    }
}
