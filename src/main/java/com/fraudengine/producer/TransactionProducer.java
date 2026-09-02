package com.fraudengine.producer;

import com.fraudengine.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes transaction events to the Kafka 'transactions' topic.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. Message Key = accountId
 *    All transactions for the same account go to the same Kafka partition.
 *    This guarantees per-account ordering, which is critical because the
 *    velocity fraud rule needs to see transactions in sequence.
 *
 * 2. Idempotent Producer (configured in application.yml)
 *    enable.idempotence=true prevents duplicate messages when the producer
 *    retries due to transient network failures. In a banking context, a
 *    duplicate event could mean a double-charge to a customer.
 *
 * 3. acks=all
 *    The producer waits for all in-sync replicas to acknowledge the write.
 *    This is the strongest durability guarantee — no message loss even if
 *    a broker crashes immediately after acknowledgment.
 */
@Service
@ConditionalOnBean(KafkaTemplate.class)  // Only created when Kafka is configured
public class TransactionProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionProducer.class);
    public static final String TOPIC = "transactions";

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    public TransactionProducer(KafkaTemplate<String, Transaction> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a transaction to Kafka asynchronously.
     *
     * @return a CompletableFuture that completes when Kafka acknowledges the write
     */
    public CompletableFuture<SendResult<String, Transaction>> send(Transaction transaction) {
        log.info("Publishing transaction {} for account {} (amount: ${})",
                transaction.getTransactionId(),
                transaction.getAccountId(),
                String.format("%.2f", transaction.getAmount()));

        return kafkaTemplate.send(TOPIC, transaction.getAccountId(), transaction)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish transaction {}: {}",
                                transaction.getTransactionId(), ex.getMessage());
                    } else {
                        log.debug("Transaction {} → partition {} offset {}",
                                transaction.getTransactionId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
