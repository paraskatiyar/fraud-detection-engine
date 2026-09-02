package com.fraudengine.service;

import com.fraudengine.model.FraudAlert;
import com.fraudengine.repository.FraudAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for fraud alert persistence and querying.
 *
 * Handles:
 * - Saving alerts with idempotency (same transaction + rule won't create duplicates)
 * - Paginated querying with filters (account, status)
 * - Status updates (analyst workflow)
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final FraudAlertRepository repository;

    public AlertService(FraudAlertRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist fraud alerts with idempotency protection.
     *
     * If the same transactionId + rule combination already exists in the DB,
     * we skip the insert. This prevents duplicate alerts when a Kafka consumer
     * reprocesses a message (e.g., after a rebalance or restart).
     */
    @Transactional
    public void saveAlerts(List<FraudAlert> alerts) {
        for (FraudAlert alert : alerts) {
            if (!repository.existsByTransactionIdAndTriggeredRule(
                    alert.getTransactionId(), alert.getTriggeredRule())) {
                repository.save(alert);
                log.info("Saved alert: rule={}, account={}, amount=${}",
                        alert.getTriggeredRule(),
                        alert.getAccountId(),
                        String.format("%.2f", alert.getAmount()));
            } else {
                log.debug("Duplicate alert skipped: txn={}, rule={}",
                        alert.getTransactionId(), alert.getTriggeredRule());
            }
        }
    }

    public Page<FraudAlert> getAlerts(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<FraudAlert> getAlertsByAccount(String accountId, Pageable pageable) {
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    public Page<FraudAlert> getAlertsByStatus(FraudAlert.AlertStatus status, Pageable pageable) {
        return repository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Update alert status — models the analyst review workflow.
     * OPEN → REVIEWED → DISMISSED
     */
    @Transactional
    public FraudAlert updateStatus(Long alertId, FraudAlert.AlertStatus newStatus) {
        FraudAlert alert = repository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Alert not found with id: " + alertId));
        alert.setStatus(newStatus);
        log.info("Alert {} status updated to {}", alertId, newStatus);
        return repository.save(alert);
    }
}
