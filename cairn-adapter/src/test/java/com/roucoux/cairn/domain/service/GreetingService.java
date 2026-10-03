package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.port.in.GreetUseCase;
import com.roucoux.cairn.domain.port.in.WaveUseCase;

public class GreetingService implements GreetUseCase, WaveUseCase {

    @Override
    public String greet(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("blank name");
        }
        return "hello " + name;
    }

    @Override
    public void wave() {}

    public String notAPortMethod() {
        return "plain";
    }
}
