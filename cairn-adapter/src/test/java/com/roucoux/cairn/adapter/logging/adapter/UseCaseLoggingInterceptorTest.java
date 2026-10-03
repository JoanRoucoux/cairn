package com.roucoux.cairn.adapter.logging.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roucoux.cairn.domain.port.in.GreetUseCase;
import com.roucoux.cairn.domain.service.GreetingService;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;

class UseCaseLoggingInterceptorTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("cairn.usecase");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void logsASuccessfulCallAtInfoWithItsFields() {
        GreetingService service = proxied(new GreetingService());

        assertThat(service.greet("alex")).isEqualTo("hello alex");

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).matches("use case Greet\\.greet succeeded in \\d+ ms");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(fields(event))
                    .containsEntry("useCase", "Greet")
                    .containsEntry("method", "greet")
                    .containsEntry("outcome", "success")
                    .containsKey("durationMs");
        });
    }

    @Test
    void rethrowsTheSameExceptionAndLogsAWarningWithoutStack() {
        GreetingService service = proxied(new GreetingService());

        assertThatThrownBy(() -> service.greet(" "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("blank name");

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .matches("use case Greet\\.greet failed in \\d+ ms: IllegalArgumentException: blank name");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(fields(event))
                    .containsEntry("outcome", "failure")
                    .containsEntry("exception", "IllegalArgumentException");
        });
    }

    @Test
    void namesTheRightPortWhenTheServiceImplementsSeveral() {
        GreetingService service = proxied(new GreetingService());

        service.wave();

        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> assertThat(fields(event)).containsEntry("useCase", "Wave"));
    }

    @Test
    void doesNotLogAMethodNoPortDeclares() {
        GreetingService service = proxied(new GreetingService());

        service.notAPortMethod();
        service.toString();

        assertThat(appender.list).isEmpty();
    }

    @Test
    void worksThroughTheInterfaceToo() {
        GreetUseCase useCase = proxied(new GreetingService());

        useCase.greet("alex");

        assertThat(appender.list).hasSize(1);
    }

    private static GreetingService proxied(GreetingService target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new UseCaseLoggingInterceptor(GreetingService.class));
        return (GreetingService) factory.getProxy();
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
