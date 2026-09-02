package com.fraudengine.config;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handling with Dead Letter Queue (DLQ) support.
 *
 * WHY DLQ MATTERS IN BANKING:
 * In a financial system, you can never silently drop a message. Every
 * transaction must be accounted for — either processed successfully or
 * routed to an error queue for human investigation.
 *
 * HOW IT WORKS:
 * 1. Consumer attempts to process a message.
 * 2. If it fails, Spring Kafka retries (3 times, 1 second apart).
 * 3. After all retries are exhausted, the message is forwarded to the
 *    Dead Letter Topic (original-topic-name + ".DLT" suffix).
 * 4. The operations team can inspect DLT messages, fix the root cause,
 *    and replay them.
 *
 * This prevents the "poison pill" problem where a single malformed
 * message blocks the entire consumer from making progress.
 */
@Configuration
@ConditionalOnBean(KafkaTemplate.class)  // Skip when Kafka is disabled (dev profile)
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public CommonErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        // Route failed records to the Dead Letter Topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> {
                    log.error("Message exhausted retries. Sending to DLT. " +
                              "Topic: {}, Partition: {}, Offset: {}, Key: {}, Error: {}",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), ex.getMessage());
                    // Route to <original-topic>.DLT, same partition
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );

        // Retry 3 times with 1-second fixed backoff before giving up
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3)  // interval=1s, maxAttempts=3
        );

        // Log each retry attempt for observability
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry attempt {} for record [topic={}, partition={}, offset={}, key={}]",
                        deliveryAttempt,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key())
        );

        return errorHandler;
    }
}
