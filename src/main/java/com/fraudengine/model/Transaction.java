package com.fraudengine.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Represents a financial transaction flowing through the system.
 *
 * This is a plain DTO (Data Transfer Object) — NOT a JPA entity.
 * It is serialized to JSON when published to Kafka, and deserialized
 * back when consumed. Jakarta Validation annotations enforce basic
 * input quality at the REST layer.
 */
public class Transaction {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "Account ID is required")
    private String accountId;

    @Positive(message = "Amount must be positive")
    private double amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
    private String currency;

    private String merchantId;

    private String location;

    private long timestamp;

    private String deviceId;

    /** Default constructor required by Jackson for JSON deserialization */
    public Transaction() {
    }

    public Transaction(String transactionId, String accountId, double amount,
                       String currency, String merchantId, String location,
                       long timestamp, String deviceId) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.location = location;
        this.timestamp = timestamp;
        this.deviceId = deviceId;
    }

    // ── Getters & Setters ────────────────────────────────────────────────

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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "txnId='" + transactionId + '\'' +
                ", account='" + accountId + '\'' +
                ", amount=$" + String.format("%.2f", amount) +
                ", currency='" + currency + '\'' +
                ", merchant='" + merchantId + '\'' +
                '}';
    }
}
