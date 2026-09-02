package com.fraudengine.repository;

import com.fraudengine.model.FraudAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for fraud alerts.
 *
 * Spring Data auto-generates the implementation at runtime based on the
 * method names. No boilerplate SQL needed — method naming conventions
 * like "findByAccountIdOrderByCreatedAtDesc" are parsed into queries.
 */
@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    /** Check if an alert already exists for this transaction + rule combo (idempotency) */
    boolean existsByTransactionIdAndTriggeredRule(String transactionId, String triggeredRule);

    /** All alerts, newest first */
    Page<FraudAlert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Filter by account */
    Page<FraudAlert> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);

    /** Filter by status (OPEN / REVIEWED / DISMISSED) */
    Page<FraudAlert> findByStatusOrderByCreatedAtDesc(FraudAlert.AlertStatus status, Pageable pageable);
}
