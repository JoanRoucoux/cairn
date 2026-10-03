package com.roucoux.cairn.infrastructure.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
class AuthenticationEventLogger {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventLogger.class);

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        log.info("authentication succeeded for {}", event.getAuthentication().getName());
    }

    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        log.warn(
                "authentication failed for {}: {}",
                event.getAuthentication().getName(),
                event.getException().getClass().getSimpleName());
    }
}
