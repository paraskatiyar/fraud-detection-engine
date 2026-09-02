package com.fraudengine.controller;

import com.fraudengine.model.FraudAlert;
import com.fraudengine.model.Transaction;
import com.fraudengine.producer.TransactionProducer;
import com.fraudengine.service.AlertService;
import com.fraudengine.service.FraudRuleEngine;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for submitting financial transactions.
 *
 * Endpoints:
 *   POST /api/transactions          — Submit a single transaction
 *   POST /api/transactions/simulate — Generate demo transactions
 *
 * IMPORTANT: Returns 202 Accepted (not 200 OK).
 * The transaction is published to Kafka and processed asynchronously.
 * The caller doesn't wait for fraud evaluation — this mimics how real
 * payment gateways work: the authorization is fast, and fraud checks
 * happen in the background.
 *
 * DEV MODE: When Kafka is unavailable (dev profile), transactions are
 * processed synchronously through the fraud rule engine directly.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    // Optional: may be null in dev profile when Kafka is disabled
    @Autowired(required = false)
    private TransactionProducer producer;

    private final FraudRuleEngine ruleEngine;
    private final AlertService alertService;

    public TransactionController(FraudRuleEngine ruleEngine, AlertService alertService) {
        this.ruleEngine = ruleEngine;
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submitTransaction(
            @Valid @RequestBody Transaction transaction) {

        // Auto-generate ID and timestamp if not provided by the caller
        if (transaction.getTransactionId() == null
                || transaction.getTransactionId().isBlank()) {
            transaction.setTransactionId(UUID.randomUUID().toString());
        }
        if (transaction.getTimestamp() == 0) {
            transaction.setTimestamp(Instant.now().toEpochMilli());
        }

        if (producer != null) {
            // PRODUCTION MODE: publish to Kafka (async processing)
            producer.send(transaction);
        } else {
            // DEV MODE: process synchronously (no Kafka)
            log.info("[DEV] Processing transaction directly (Kafka disabled)");
            processDirectly(transaction);
        }

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "ACCEPTED",
                        "transactionId", transaction.getTransactionId(),
                        "message", "Transaction submitted for fraud evaluation"
                ));
    }

    /**
     * Simulate a batch of transactions for demo/testing.
     * Generates a mix of normal and suspicious transactions so you can
     * see the fraud engine in action without a separate load generator.
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulateTransactions(
            @RequestParam(defaultValue = "10") int count) {

        log.info("Simulating {} transactions...", count);
        int alertCount = 0;

        String[] accounts = {"ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005"};
        String[] merchants = {"MER-AMAZON", "MER-STARBUCKS", "MER-WALMART", "MER-APPLE", "MER-SHELL"};
        String[] locations = {"New York", "London", "Mumbai", "Tokyo", "Berlin"};

        for (int i = 0; i < count; i++) {
            Transaction txn = new Transaction();
            txn.setTransactionId(UUID.randomUUID().toString());
            txn.setAccountId(accounts[i % accounts.length]);
            txn.setCurrency("USD");
            txn.setMerchantId(merchants[i % merchants.length]);
            txn.setLocation(locations[i % locations.length]);
            txn.setTimestamp(Instant.now().toEpochMilli());
            txn.setDeviceId("DEVICE-" + (i % 3));

            // Make ~20% of transactions suspicious (high amount)
            if (i % 5 == 0) {
                txn.setAmount(15000.00 + (Math.random() * 35000));
            } else {
                txn.setAmount(10.00 + (Math.random() * 500));
            }

            if (producer != null) {
                producer.send(txn);
            } else {
                alertCount += processDirectly(txn);
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "SIMULATED",
                "count", count,
                "alertsGenerated", alertCount,
                "message", count + " transactions processed"
        ));
    }

    /**
     * Direct processing path when Kafka is unavailable (dev mode).
     * Returns the number of alerts generated.
     */
    private int processDirectly(Transaction transaction) {
        List<FraudAlert> alerts = ruleEngine.evaluate(transaction);
        if (!alerts.isEmpty()) {
            alertService.saveAlerts(alerts);
            log.warn("🚨 {} fraud alert(s) for transaction {}",
                    alerts.size(), transaction.getTransactionId());
        }
        return alerts.size();
    }
}
