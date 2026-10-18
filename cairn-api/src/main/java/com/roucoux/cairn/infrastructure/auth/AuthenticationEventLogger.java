package com.roucoux.cairn.infrastructure.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class AuthenticationEventLogger {

    static final int MAX_USERNAME_LENGTH = 32;

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventLogger.class);

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        log.info("authentication succeeded for {}", event.getAuthentication().getName());
    }

    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        log.warn(
                "authentication failed for {} from {}: {}",
                truncated(event.getAuthentication().getName()),
                clientAddress(),
                event.getException().getClass().getSimpleName());
    }

    static String truncated(String username) {
        if (username == null) {
            return "";
        }
        return username.length() <= MAX_USERNAME_LENGTH ? username : username.substring(0, MAX_USERNAME_LENGTH);
    }

    private static String clientAddress() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr()
                : "unknown";
    }
}
