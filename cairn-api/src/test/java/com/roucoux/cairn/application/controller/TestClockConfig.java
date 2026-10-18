package com.roucoux.cairn.application.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfig {

    @Bean
    Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-26T20:00:00Z"), ZoneOffset.UTC);
    }
}
