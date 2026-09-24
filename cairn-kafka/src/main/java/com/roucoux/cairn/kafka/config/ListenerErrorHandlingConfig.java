package com.roucoux.cairn.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * A listener that keeps failing after 3 retries spaced 2 s is logged and skipped rather than
 * blocking its partition forever: {@code DefaultErrorHandler}'s default recoverer already just
 * logs.
 */
@Configuration(proxyBeanMethods = false)
class ListenerErrorHandlingConfig {

    private static final long BACK_OFF_INTERVAL_MS = 2000L;
    private static final long RETRIES = 3L;

    @Bean
    DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(BACK_OFF_INTERVAL_MS, RETRIES));
    }
}
