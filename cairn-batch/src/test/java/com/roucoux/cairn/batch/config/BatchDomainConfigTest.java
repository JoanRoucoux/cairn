package com.roucoux.cairn.batch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/**
 * {@code CoinGeckoQuoteAdapter} lives in {@code cairn-adapter}, reached by {@code cairn-batch}
 * only at runtime scope — this test stands in for it with a bean shaped the same way (request
 * scope, {@code TARGET_CLASS} proxy, same bean name) to prove the rescoping postprocessor genuinely
 * flips the target bean definition's scope, rather than trusting the bean-name string by eye.
 */
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
}
