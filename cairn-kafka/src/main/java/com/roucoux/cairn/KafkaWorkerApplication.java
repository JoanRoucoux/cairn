package com.roucoux.cairn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Third entry point over the same hexagon: a long-running worker, unlike {@code BatchApplication},
 * that declares the Kafka topics. Deliberately in the base package, so the component scan reaches
 * the adapters exactly as the other applications' do.
 */
@SpringBootApplication
@EnableScheduling
public class KafkaWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaWorkerApplication.class, args);
    }
}
