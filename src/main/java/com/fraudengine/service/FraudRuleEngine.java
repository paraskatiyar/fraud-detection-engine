package com.fraudengine.service;

import com.fraudengine.model.FraudAlert;
import com.fraudengine.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates all fraud detection rules against incoming transactions.
 *
 * DESIGN PATTERN — Composite + Strategy:
 * This class doesn't know about specific rules. It iterates over all
 * {@link FraudRule} implementations that Spring injects automatically.
 * Adding a new rule requires zero changes to this class.
 *
 * This is the "engine" that a real bank's fraud system would have:
 * a pluggable rule evaluation framework where rules can be added,
 * removed, or tuned independently.
 */
@Service
public class FraudRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleEngine.class);

    private final List<FraudRule> rules;

    /**
     * Spring auto-injects ALL beans implementing {@link FraudRule}.
     * No manual registration needed — just create a new @Component
     * implementing FraudRule and it's automatically picked up.
     */
    public FraudRuleEngine(List<FraudRule> rules) {
        this.rules = rules;
        log.info("FraudRuleEngine initialized with {} rules: {}",
                rules.size(),
                rules.stream().map(FraudRule::ruleName).toList());
    }

    /**
     * Evaluates all registered fraud rules against a transaction.
     *
     * @param transaction the transaction to evaluate
     * @return list of generated alerts (one per triggered rule).
     *         Empty list means the transaction is legitimate.
     */
    public List<FraudAlert> evaluate(Transaction transaction) {
        List<FraudAlert> alerts = new ArrayList<>();

        for (FraudRule rule : rules) {
            rule.evaluate(transaction).ifPresent(detail -> {
                FraudAlert alert = FraudAlert.from(
                        transaction, rule.ruleName(), detail);
                alerts.add(alert);
                log.info("Rule [{}] triggered for transaction {}",
                        rule.ruleName(), transaction.getTransactionId());
            });
        }

        if (alerts.isEmpty()) {
            log.debug("Transaction {} passed all {} fraud checks",
                    transaction.getTransactionId(), rules.size());
        }

        return alerts;
    }
}
