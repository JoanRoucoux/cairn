package com.roucoux.cairn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Second entry point over the same hexagon: Spring Boot launches every {@code Job} bean on
 * startup and the process exits when they finish. Deliberately in the base package, so the
 * component scan reaches the adapters exactly as the API application's does.
 */
@SpringBootApplication
public class BatchApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchApplication.class, args)));
    }
}
