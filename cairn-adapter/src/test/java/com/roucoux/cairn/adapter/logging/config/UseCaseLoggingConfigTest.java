package com.roucoux.cairn.adapter.logging.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UseCaseLoggingConfigTest {

    @Test
    void declaresThePostProcessor() {
        assertThat(UseCaseLoggingConfig.useCaseLoggingPostProcessor()).isNotNull();
    }
}
