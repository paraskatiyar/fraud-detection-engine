package com.fraudengine.consumer;

import com.fraudengine.model.FraudAlert;
import com.fraudengine.model.Transaction;
import com.fraudengine.service.AlertService;
import com.fraudengine.service.FraudRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer that processes transactions and evaluates fraud rules.
 *
 * FLOW:
 *   Kafka (transactions topic) → Consumer → FraudRuleEngine → AlertService → PostgreSQL
 *
 * ERROR HANDLING:
 * If this method throws any exception (deserialization failure, DB error, etc.),
 * the {@link com.fraudengine.config.KafkaConfig} error handler kicks in:
 *   1. Retry 3 times (1 second apart)
 *   2. If still failing, publish to Dead Letter Topic (transactions.DLT)
 *
 * This prevents a single "poison pill" message from blocking the consumer.
 *
 * CONSUMER GROUP:
 * All instances of this application use the same group-id ("fraud-detection-group").
 * Kafka automatically distributes partitions across instances, enabling horizontal
 * scaling without code changes.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)  // Only active when Kafka is configured
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final FraudRuleEngine ruleEngine;
    private final AlertService alertService;

    public TransactionConsumer(FraudRuleEngine ruleEngine, AlertService alertService) {
        this.ruleEngine = ruleEngine;
        this.alertService = alertService;
    }

    @KafkaListener(
            topics = "transactions",
            groupId = "fraud-detection-group"
    )
    public void consume(
            @Payload Transaction transaction,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed: {} [partition={}, offset={}]",
                transaction, partition, offset);

        // Run all fraud rules
        List<FraudAlert> alerts = ruleEngine.evaluate(transaction);

        // Persist any triggered alerts to PostgreSQL
        if (!alerts.isEmpty()) {
            alertService.saveAlerts(alerts);
            log.warn("🚨 {} fraud alert(s) for transaction {}",
                    alerts.size(), transaction.getTransactionId());
        }
    }
}
