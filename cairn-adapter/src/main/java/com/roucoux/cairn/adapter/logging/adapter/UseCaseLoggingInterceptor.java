package com.roucoux.cairn.adapter.logging.adapter;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class UseCaseLoggingInterceptor implements MethodInterceptor {

    static final String INBOUND_PORT_PACKAGE = ".domain.port.in";

    private static final Logger log = LoggerFactory.getLogger("cairn.usecase");
    private static final String SUFFIX = "UseCase";

    private final Class<?> targetClass;
    private final Map<Method, Optional<String>> useCases = new ConcurrentHashMap<>();

    public UseCaseLoggingInterceptor(Class<?> targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Optional<String> useCase = useCases.computeIfAbsent(invocation.getMethod(), this::useCaseOf);
        if (useCase.isEmpty()) {
            return invocation.proceed();
        }
        String method = invocation.getMethod().getName();
        long start = System.nanoTime();
        try {
            Object result = invocation.proceed();
            long durationMs = elapsedMs(start);
            log.atInfo()
                    .addKeyValue("useCase", useCase.get())
                    .addKeyValue("method", method)
                    .addKeyValue("outcome", "success")
                    .addKeyValue("durationMs", durationMs)
                    .log("use case {}.{} succeeded in {} ms", useCase.get(), method, durationMs);
            return result;
        } catch (Throwable failure) {
            long durationMs = elapsedMs(start);
            String exception = failure.getClass().getSimpleName();
            log.atWarn()
                    .addKeyValue("useCase", useCase.get())
                    .addKeyValue("method", method)
                    .addKeyValue("outcome", "failure")
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("exception", exception)
                    .log("use case {}.{} failed in {} ms: {}", useCase.get(), method, durationMs, exception);
            throw failure;
        }
    }

    private Optional<String> useCaseOf(Method method) {
        return ClassUtils.getAllInterfacesForClassAsSet(targetClass).stream()
                .filter(port -> port.getPackageName().endsWith(INBOUND_PORT_PACKAGE))
                .filter(port -> ReflectionUtils.findMethod(port, method.getName(), method.getParameterTypes()) != null)
                .map(Class::getSimpleName)
                .map(name -> name.endsWith(SUFFIX) ? name.substring(0, name.length() - SUFFIX.length()) : name)
                .findFirst();
    }

    private static long elapsedMs(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
