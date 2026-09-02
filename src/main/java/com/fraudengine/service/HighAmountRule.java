package com.fraudengine.service;

import com.fraudengine.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Flags transactions that exceed a configurable amount threshold.
 *
 * BUSINESS CONTEXT:
 * Banks are legally required (e.g., under BSA/AML regulations) to report
 * transactions above certain thresholds. High-value transactions also
 * carry greater financial risk if fraudulent. The threshold is externalized
 * to application.yml so compliance teams can adjust it without code changes.
 */
@Component
public class HighAmountRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(HighAmountRule.class);

    private final double threshold;

    public HighAmountRule(
            @Value("${fraud.rules.high-amount-threshold:10000.00}") double threshold) {
        this.threshold = threshold;
        log.info("HighAmountRule initialized with threshold: ${}", threshold);
    }

    @Override
    public Optional<String> evaluate(Transaction transaction) {
        if (transaction.getAmount() > threshold) {
            String detail = String.format(
                    "Transaction amount $%.2f exceeds threshold $%.2f",
                    transaction.getAmount(), threshold
            );
            log.warn("HIGH_AMOUNT triggered for account {}: {}",
                    transaction.getAccountId(), detail);
            return Optional.of(detail);
        }
        return Optional.empty();
    }

    @Override
    public String ruleName() {
        return "HIGH_AMOUNT";
    }
}
