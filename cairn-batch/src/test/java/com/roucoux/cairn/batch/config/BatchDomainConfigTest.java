package com.roucoux.cairn.batch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

class BatchDomainConfigTest {

    @Component("coinGeckoQuoteAdapter")
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    static class RequestScopedStandIn {}

    @Test
    void rescopesTheCoinGeckoAdaptersTargetBeanDefinitionFromRequestToStep() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(RequestScopedStandIn.class);
            String targetBeanName = ScopedProxyUtils.getTargetBeanName("coinGeckoQuoteAdapter");
            assertThat(context.getBeanFactory()
                            .getBeanDefinition(targetBeanName)
                            .getScope())
                    .isEqualTo("request");

            BeanFactoryPostProcessor processor = BatchDomainConfig.coinGeckoQuoteAdapterStepScoped();
            processor.postProcessBeanFactory(context.getBeanFactory());

            assertThat(context.getBeanFactory()
                            .getBeanDefinition(targetBeanName)
                            .getScope())
                    .isEqualTo("step");
        }
    }

    @Test
    void doesNothingWhenNoScopedTargetIsRegistered() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            BeanFactoryPostProcessor processor = BatchDomainConfig.coinGeckoQuoteAdapterStepScoped();

            assertThatCode(() -> processor.postProcessBeanFactory(context.getBeanFactory()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void clockIsFixedToTheConfiguredZone() {
        Clock clock = new BatchDomainConfig().clock("Europe/Paris");

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Paris"));
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.now(ZoneId.of("Europe/Paris")));
    }
}
