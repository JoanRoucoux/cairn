package com.roucoux.cairn.kafka.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the worker. Empty of domain services for now, exactly like
 * {@code BatchDomainConfig} before its first job: consumers and schedulers land in later lots.
 */
@Configuration(proxyBeanMethods = false)
class WorkerDomainConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
