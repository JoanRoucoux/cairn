package com.roucoux.cairn.kafka.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root of the worker. */
@Configuration(proxyBeanMethods = false)
class WorkerDomainConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
