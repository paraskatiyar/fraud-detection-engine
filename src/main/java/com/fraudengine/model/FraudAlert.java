package com.fraudengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA entity representing a detected fraud alert.
 *
 * Each alert maps to one transaction + one triggered rule. A single
 * transaction can generate multiple alerts if multiple rules fire.
 *
 * The status field models the analyst workflow:
 *   OPEN → REVIEWED → DISMISSED
 * This is how real bank fraud teams operate: an alert is created,
 * an analyst reviews it, and they either escalate or dismiss it.
 */
@Entity
@Table(
    name = "fraud_alerts",
    indexes = {
        @Index(name = "idx_alert_account", columnList = "accountId"),
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_created", columnList = "createdAt")
    }
)
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private double amount;

    /** The name of the rule that triggered this alert (e.g. "HIGH_AMOUNT") */
    @Column(nullable = false)
    private String triggeredRule;

    /** Human-readable explanation of why the rule triggered */
    @Column(length = 1000)
    private String ruleDetails;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.OPEN;

    /** Analyst workflow states */
    public enum AlertStatus {
        OPEN,       // Newly created, awaiting review
        REVIEWED,   // Analyst has examined the alert
        DISMISSED   // Analyst determined it's a false positive
    }

    // ── Constructors ─────────────────────────────────────────────────────

    /** Default constructor for JPA */
    public FraudAlert() {
    }

    /**
     * Factory method: creates an alert from a transaction and a triggered rule.
     * Encapsulates the mapping logic in one place.
     */
    public static FraudAlert from(Transaction transaction, String rule, String details) {
        FraudAlert alert = new FraudAlert();
        alert.transactionId = transaction.getTransactionId();
        alert.accountId = transaction.getAccountId();
        alert.amount = transaction.getAmount();
        alert.triggeredRule = rule;
        alert.ruleDetails = details;
        alert.createdAt = LocalDateTime.now();
        alert.status = AlertStatus.OPEN;
        return alert;
    }

    // ── Getters & Setters ────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getTriggeredRule() {
        return triggeredRule;
    }

    public void setTriggeredRule(String triggeredRule) {
        this.triggeredRule = triggeredRule;
    }

    public String getRuleDetails() {
        return ruleDetails;
    }

    public void setRuleDetails(String ruleDetails) {
        this.ruleDetails = ruleDetails;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }
}
