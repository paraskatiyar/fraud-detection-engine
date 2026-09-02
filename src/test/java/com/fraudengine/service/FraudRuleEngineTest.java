package com.fraudengine.service;

import com.fraudengine.model.FraudAlert;
import com.fraudengine.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the fraud rule engine.
 *
 * These tests run without Spring context, Kafka, or any database.
 * They are fast (~10ms each), deterministic, and test pure business logic.
 *
 * This is what interviewers look for:
 * - Clean test structure (Arrange / Act / Assert)
 * - Descriptive test names explaining behavior
 * - Edge case coverage (boundary values, independent accounts)
 * - @Nested grouping by concern
 */
class FraudRuleEngineTest {

    private FraudRuleEngine engine;

    @BeforeEach
    void setUp() {
        // Manually wire rules — no Spring context needed for unit tests.
        // This makes tests fast and independent of framework configuration.
        List<FraudRule> rules = List.of(
                new HighAmountRule(10_000.00),
                new VelocityRule(300, 5)  // 5 max in 300-second window
        );
        engine = new FraudRuleEngine(rules);
    }

    /** Helper: creates a transaction with the given account and amount */
    private Transaction createTransaction(String accountId, double amount) {
        return new Transaction(
                UUID.randomUUID().toString(),
                accountId,
                amount,
                "USD",
                "MER-TEST",
                "New York",
                Instant.now().toEpochMilli(),
                "DEVICE-1"
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // HIGH AMOUNT RULE
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("High Amount Rule")
    class HighAmountRuleTests {

        @Test
        @DisplayName("should flag transactions above the $10,000 threshold")
        void shouldFlagHighAmount() {
            Transaction txn = createTransaction("ACC-001", 15_000.00);

            List<FraudAlert> alerts = engine.evaluate(txn);

            assertThat(alerts)
                    .isNotEmpty()
                    .anyMatch(a -> a.getTriggeredRule().equals("HIGH_AMOUNT"));
        }

        @Test
        @DisplayName("should NOT flag transactions below the threshold")
        void shouldNotFlagNormalAmount() {
            Transaction txn = createTransaction("ACC-001", 500.00);

            List<FraudAlert> alerts = engine.evaluate(txn);

            assertThat(alerts)
                    .noneMatch(a -> a.getTriggeredRule().equals("HIGH_AMOUNT"));
        }

        @Test
        @DisplayName("should NOT flag transactions exactly at the threshold (boundary)")
        void shouldNotFlagExactThreshold() {
            // $10,000.00 is the threshold — rule triggers on > threshold, not >=
            Transaction txn = createTransaction("ACC-001", 10_000.00);

            List<FraudAlert> alerts = engine.evaluate(txn);

            assertThat(alerts)
                    .noneMatch(a -> a.getTriggeredRule().equals("HIGH_AMOUNT"));
        }

        @Test
        @DisplayName("should include descriptive details in the alert")
        void shouldIncludeDetailsInAlert() {
            Transaction txn = createTransaction("ACC-001", 25_000.00);

            List<FraudAlert> alerts = engine.evaluate(txn);

            FraudAlert alert = alerts.stream()
                    .filter(a -> a.getTriggeredRule().equals("HIGH_AMOUNT"))
                    .findFirst()
                    .orElseThrow();

            assertThat(alert.getRuleDetails())
                    .contains("25000.00")
                    .contains("10000.00");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // VELOCITY RULE
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Velocity Rule")
    class VelocityRuleTests {

        @Test
        @DisplayName("should flag account exceeding max transactions in time window")
        void shouldFlagRapidTransactions() {
            String accountId = "ACC-RAPID";

            // Send 6 transactions — max is 5, so the 6th should trigger
            List<FraudAlert> alerts = List.of();
            for (int i = 0; i < 6; i++) {
                alerts = engine.evaluate(createTransaction(accountId, 100.00));
            }

            assertThat(alerts)
                    .anyMatch(a -> a.getTriggeredRule().equals("VELOCITY"));
        }

        @Test
        @DisplayName("should NOT flag account within velocity limit")
        void shouldNotFlagNormalVelocity() {
            String accountId = "ACC-NORMAL";

            // Send only 3 transactions — well within the limit of 5
            List<FraudAlert> alerts = List.of();
            for (int i = 0; i < 3; i++) {
                alerts = engine.evaluate(createTransaction(accountId, 100.00));
            }

            assertThat(alerts)
                    .noneMatch(a -> a.getTriggeredRule().equals("VELOCITY"));
        }

        @Test
        @DisplayName("should track accounts independently")
        void shouldTrackAccountsIndependently() {
            // Send 3 transactions each for two different accounts
            // Neither should trigger (both under limit of 5)
            for (int i = 0; i < 3; i++) {
                List<FraudAlert> alertsA = engine.evaluate(
                        createTransaction("ACC-A", 100.00));
                List<FraudAlert> alertsB = engine.evaluate(
                        createTransaction("ACC-B", 100.00));

                assertThat(alertsA)
                        .noneMatch(a -> a.getTriggeredRule().equals("VELOCITY"));
                assertThat(alertsB)
                        .noneMatch(a -> a.getTriggeredRule().equals("VELOCITY"));
            }
        }

        @Test
        @DisplayName("should NOT flag at exactly the max count (boundary)")
        void shouldNotFlagExactlyAtMax() {
            String accountId = "ACC-BOUNDARY";

            // Send exactly 5 transactions — should NOT trigger (rule is > max, not >=)
            List<FraudAlert> alerts = List.of();
            for (int i = 0; i < 5; i++) {
                alerts = engine.evaluate(createTransaction(accountId, 100.00));
            }

            assertThat(alerts)
                    .noneMatch(a -> a.getTriggeredRule().equals("VELOCITY"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // COMBINED RULES
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Combined Rules")
    class CombinedTests {

        @Test
        @DisplayName("should trigger multiple rules simultaneously")
        void shouldTriggerMultipleRules() {
            String accountId = "ACC-MULTI";

            // Build up velocity first (5 small transactions)
            for (int i = 0; i < 5; i++) {
                engine.evaluate(createTransaction(accountId, 100.00));
            }

            // Now send a high-amount transaction that also exceeds velocity
            Transaction txn = createTransaction(accountId, 50_000.00);
            List<FraudAlert> alerts = engine.evaluate(txn);

            assertThat(alerts).hasSize(2);
            assertThat(alerts)
                    .anyMatch(a -> a.getTriggeredRule().equals("HIGH_AMOUNT"));
            assertThat(alerts)
                    .anyMatch(a -> a.getTriggeredRule().equals("VELOCITY"));
        }

        @Test
        @DisplayName("alerts should contain correct transaction metadata")
        void alertsShouldContainTransactionMetadata() {
            Transaction txn = createTransaction("ACC-META", 20_000.00);

            List<FraudAlert> alerts = engine.evaluate(txn);

            FraudAlert alert = alerts.get(0);
            assertThat(alert.getTransactionId()).isEqualTo(txn.getTransactionId());
            assertThat(alert.getAccountId()).isEqualTo("ACC-META");
            assertThat(alert.getAmount()).isEqualTo(20_000.00);
            assertThat(alert.getStatus()).isEqualTo(FraudAlert.AlertStatus.OPEN);
            assertThat(alert.getCreatedAt()).isNotNull();
        }
    }
}
