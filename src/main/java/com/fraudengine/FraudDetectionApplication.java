package com.fraudengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Real-Time Fraud Detection Engine
 *
 * A Spring Boot application that:
 * 1. Accepts financial transactions via REST API
 * 2. Publishes them to a Kafka topic
 * 3. Consumes and evaluates them against configurable fraud rules
 * 4. Persists detected fraud alerts to PostgreSQL
 * 5. Routes failed messages to a Dead Letter Topic for investigation
 */
@SpringBootApplication
public class FraudDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionApplication.class, args);
    }
}
