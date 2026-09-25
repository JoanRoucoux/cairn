package com.roucoux.cairn.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class ListenerErrorHandlingConfig {

    private static final long BACK_OFF_INTERVAL_MS = 2000L;
    private static final long RETRIES = 3L;

    @Bean
    DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(BACK_OFF_INTERVAL_MS, RETRIES));
    }
}
