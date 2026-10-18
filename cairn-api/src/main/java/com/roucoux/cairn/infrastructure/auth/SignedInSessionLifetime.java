package com.roucoux.cairn.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

final class SignedInSessionLifetime implements SessionAuthenticationStrategy {

    private final int seconds;

    SignedInSessionLifetime(Duration lifetime) {
        this.seconds = Math.toIntExact(lifetime.toSeconds());
    }

    @Override
    public void onAuthentication(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        request.getSession().setMaxInactiveInterval(seconds);
    }
}
