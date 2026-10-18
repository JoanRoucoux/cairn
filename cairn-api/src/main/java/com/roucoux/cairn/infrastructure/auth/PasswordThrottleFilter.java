package com.roucoux.cairn.infrastructure.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

final class PasswordThrottleFilter extends OncePerRequestFilter {

    static final String PASSWORD_SIGN_IN_URL = "/authenticate";

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static final RequestMatcher PASSWORD_SIGN_IN =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, PASSWORD_SIGN_IN_URL);

    private final PasswordGuessThrottle throttle;

    PasswordThrottleFilter(PasswordGuessThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!PASSWORD_SIGN_IN.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        Duration remaining = throttle.admit(request.getRemoteAddr());
        if (remaining.isZero()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                Long.toString((remaining.toNanos() + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND));
    }
}
