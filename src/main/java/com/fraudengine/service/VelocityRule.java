package com.fraudengine.service;

import com.fraudengine.model.Transaction;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Detects rapid-fire transactions from the same account within a time window.
 *
 * BUSINESS CONTEXT:
 * When a card or account is compromised, fraudsters typically make many
 * transactions in quick succession before the victim notices. Velocity
 * checks are one of the most effective first-line fraud defenses.
 *
 * IMPLEMENTATION:
 * Uses Caffeine (high-performance in-memory cache) to track the number
 * of transactions per account within a configurable time window. Entries
 * auto-expire after the window duration, keeping memory bounded.
 *
 * PRODUCTION NOTE:
 * In a multi-instance deployment, you'd replace Caffeine with Redis or a
 * Kafka Streams state store so all instances share the same count. For a
 * single-instance demo, Caffeine is simpler and avoids infrastructure overhead.
 */
@Component
public class VelocityRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(VelocityRule.class);

    private final int maxTransactions;
    private final Cache<String, AtomicInteger> transactionCountCache;

    public VelocityRule(
            @Value("${fraud.rules.velocity-window-seconds:300}") int windowSeconds,
            @Value("${fraud.rules.velocity-max-transactions:5}") int maxTransactions) {
        this.maxTransactions = maxTransactions;
        this.transactionCountCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)   // Bound memory: evict LRU if exceeded
                .build();

        log.info("VelocityRule initialized: max {} transactions per {} seconds",
                maxTransactions, windowSeconds);
    }

    @Override
    public Optional<String> evaluate(Transaction transaction) {
        String accountId = transaction.getAccountId();

        // Get-or-create a counter for this account. Caffeine handles
        // the TTL — the counter auto-expires after the window elapses.
        AtomicInteger count = transactionCountCache.get(
                accountId, key -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        if (currentCount > maxTransactions) {
            String detail = String.format(
                    "Account %s has %d transactions in the current window (max allowed: %d)",
                    accountId, currentCount, maxTransactions
            );
            log.warn("VELOCITY triggered: {}", detail);
            return Optional.of(detail);
        }

        return Optional.empty();
    }

    @Override
    public String ruleName() {
        return "VELOCITY";
    }
}
