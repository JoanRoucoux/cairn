package com.roucoux.cairn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KafkaWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaWorkerApplication.class, args);
    }
}
