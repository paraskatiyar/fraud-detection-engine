package com.fraudengine.service;

import com.fraudengine.model.Transaction;

import java.util.Optional;

/**
 * Strategy interface for fraud detection rules.
 *
 * DESIGN DECISION — Strategy Pattern:
 * Each fraud rule is a self-contained, independently testable component.
 * This follows the Open/Closed Principle: to add a new fraud pattern
 * (e.g., geo-impossibility, new-device detection), just create a new class
 * implementing this interface and annotate it with @Component. Spring
 * auto-discovers it. No existing code needs to change.
 *
 * In a real bank, new fraud patterns emerge constantly, so this
 * extensibility is a hard requirement.
 */
public interface FraudRule {

    /**
     * Evaluate a transaction against this rule.
     *
     * @param transaction the transaction to evaluate
     * @return a human-readable description of the violation if suspicious,
     *         or {@link Optional#empty()} if the transaction is legitimate
     */
    Optional<String> evaluate(Transaction transaction);

    /**
     * @return the unique name of this rule, used for logging and persistence
     *         (e.g., "HIGH_AMOUNT", "VELOCITY")
     */
    String ruleName();
}
