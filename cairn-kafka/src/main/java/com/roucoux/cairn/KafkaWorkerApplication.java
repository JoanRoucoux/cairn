package com.roucoux.cairn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Third entry point over the same hexagon: a long-running worker, unlike {@code BatchApplication}.
 * Deliberately in the base package, so the component scan reaches the adapters exactly as the
 * other applications' do. Declares the Kafka topics at this lot; consumers and schedulers land in
 * later lots.
 */
@SpringBootApplication
public class KafkaWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaWorkerApplication.class, args);
    }
}
