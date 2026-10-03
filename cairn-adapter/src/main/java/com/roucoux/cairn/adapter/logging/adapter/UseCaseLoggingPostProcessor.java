package com.roucoux.cairn.adapter.logging.adapter;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ClassUtils;

public class UseCaseLoggingPostProcessor implements BeanPostProcessor {

    private static final String DOMAIN_SERVICE_PACKAGE = ".domain.service";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> type = AopUtils.getTargetClass(bean);
        if (!isDomainService(type)) {
            return bean;
        }
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new UseCaseLoggingInterceptor(type));
        return factory.getProxy(type.getClassLoader());
    }

    private static boolean isDomainService(Class<?> type) {
        return type.getPackageName().endsWith(DOMAIN_SERVICE_PACKAGE)
                && ClassUtils.getAllInterfacesForClassAsSet(type).stream()
                        .anyMatch(
                                port -> port.getPackageName().endsWith(UseCaseLoggingInterceptor.INBOUND_PORT_PACKAGE));
    }
}
