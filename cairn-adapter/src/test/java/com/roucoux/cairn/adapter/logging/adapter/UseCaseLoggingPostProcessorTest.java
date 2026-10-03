package com.roucoux.cairn.adapter.logging.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.roucoux.cairn.domain.port.in.GreetUseCase;
import com.roucoux.cairn.domain.port.in.WaveUseCase;
import com.roucoux.cairn.domain.service.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;

class UseCaseLoggingPostProcessorTest {

    private final UseCaseLoggingPostProcessor postProcessor = new UseCaseLoggingPostProcessor();

    @Test
    void proxyKeepsTheConcreteTypeAndEveryPort() {
        Object bean = postProcessor.postProcessAfterInitialization(new GreetingService(), "greetingService");

        assertThat(AopUtils.isCglibProxy(bean)).isTrue();
        assertThat(bean).isInstanceOf(GreetingService.class);
        assertThat(bean).isInstanceOf(GreetUseCase.class);
        assertThat(bean).isInstanceOf(WaveUseCase.class);
        assertThat(((GreetUseCase) bean).greet("alex")).isEqualTo("hello alex");
    }

    @Test
    void leavesABeanOutsideTheDomainServicePackageUntouched() {
        GreetUseCase mock = mock(GreetUseCase.class);
        GreetUseCase stub = name -> "hi";

        assertThat(postProcessor.postProcessAfterInitialization(mock, "mock")).isSameAs(mock);
        assertThat(postProcessor.postProcessAfterInitialization(stub, "stub")).isSameAs(stub);
    }

    @Test
    void leavesABeanWithoutAnInboundPortUntouched() {
        Object plain = new Object();

        assertThat(postProcessor.postProcessAfterInitialization(plain, "plain")).isSameAs(plain);
    }
}
