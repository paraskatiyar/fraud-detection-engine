package com.fraudengine.integration;

import com.fraudengine.model.FraudAlert;
import com.fraudengine.model.Transaction;
import com.fraudengine.repository.FraudAlertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test using Testcontainers.
 *
 * This test spins up REAL Kafka and PostgreSQL containers in Docker,
 * boots the full Spring application context, and tests the complete flow:
 *
 *   REST API → Kafka Producer → Kafka Consumer → Fraud Engine → PostgreSQL
 *
 * WHY THIS TEST MATTERS FOR INTERVIEWS:
 * 1. Proves the Kafka producer/consumer wiring works with a real broker
 * 2. Validates fraud rules detect suspicious transactions end-to-end
 * 3. Confirms alerts are correctly persisted to a real database
 * 4. Shows you understand Testcontainers (a highly valued skill)
 *
 * PREREQUISITES: Docker must be running on the machine executing these tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FraudDetectionIntegrationTest {

    // ─── Test Infrastructure ─────────────────────────────────────────────

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("frauddb_test")
            .withUsername("test")
            .withPassword("test");

    /**
     * Dynamically inject container connection URLs into Spring properties.
     * This is the modern, clean way to configure Testcontainers — no hardcoded ports.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ─── Injected Beans ──────────────────────────────────────────────────

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FraudAlertRepository alertRepository;

    @LocalServerPort
    private int port;

    // ─── Tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("End-to-end: high-value transaction triggers a fraud alert in the DB")
    void highValueTransactionShouldCreateAlert() {
        // ── Arrange ──
        Transaction suspicious = new Transaction();
        suspicious.setTransactionId(UUID.randomUUID().toString());
        suspicious.setAccountId("ACC-INTEG-" + UUID.randomUUID().toString().substring(0, 6));
        suspicious.setAmount(50_000.00);  // Well above $10K threshold
        suspicious.setCurrency("USD");
        suspicious.setMerchantId("MER-SUSPECT");
        suspicious.setLocation("Test City");
        suspicious.setTimestamp(Instant.now().toEpochMilli());
        suspicious.setDeviceId("DEVICE-TEST");

        // ── Act ──
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/transactions", suspicious, Map.class);

        // ── Assert: API accepted the transaction ──
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("status", "ACCEPTED");

        // ── Assert: Fraud alert eventually appears in PostgreSQL ──
        // Awaitility polls until the assertion passes (or times out at 30s).
        // The delay accounts for Kafka propagation + consumer processing.
        String accountId = suspicious.getAccountId();
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<FraudAlert> alerts = alertRepository.findAll().stream()
                            .filter(a -> a.getAccountId().equals(accountId))
                            .toList();

                    assertThat(alerts)
                            .as("Expected a HIGH_AMOUNT alert for account " + accountId)
                            .isNotEmpty()
                            .anyMatch(alert ->
                                    alert.getTriggeredRule().equals("HIGH_AMOUNT")
                                    && alert.getAmount() == 50_000.00
                                    && alert.getStatus() == FraudAlert.AlertStatus.OPEN
                            );
                });
    }

    @Test
    @DisplayName("End-to-end: normal transaction does NOT generate a fraud alert")
    void normalTransactionShouldNotCreateAlert() {
        // ── Arrange ──
        String uniqueAccount = "ACC-CLEAN-" + UUID.randomUUID().toString().substring(0, 6);

        Transaction normal = new Transaction();
        normal.setTransactionId(UUID.randomUUID().toString());
        normal.setAccountId(uniqueAccount);
        normal.setAmount(42.50);  // Far below threshold
        normal.setCurrency("USD");
        normal.setMerchantId("MER-COFFEE");
        normal.setLocation("Test City");
        normal.setTimestamp(Instant.now().toEpochMilli());
        normal.setDeviceId("DEVICE-TEST");

        // ── Act ──
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/transactions", normal, Map.class);

        // ── Assert: API accepted ──
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // ── Assert: No alert for this account after a reasonable wait ──
        // We wait 5 seconds to give the consumer time to process, then verify
        // no alerts exist for this specific account.
        await()
                .during(5, TimeUnit.SECONDS)     // Hold for 5 seconds
                .atMost(7, TimeUnit.SECONDS)      // Fail if takes longer
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<FraudAlert> alerts = alertRepository.findAll().stream()
                            .filter(a -> a.getAccountId().equals(uniqueAccount))
                            .toList();

                    assertThat(alerts)
                            .as("Normal transaction should not generate alerts")
                            .isEmpty();
                });
    }

    @Test
    @DisplayName("REST API: can query alerts after they're created")
    void shouldQueryAlertsViaApi() {
        // ── Arrange: create a suspicious transaction ──
        String uniqueAccount = "ACC-QUERY-" + UUID.randomUUID().toString().substring(0, 6);

        Transaction txn = new Transaction();
        txn.setTransactionId(UUID.randomUUID().toString());
        txn.setAccountId(uniqueAccount);
        txn.setAmount(99_999.00);
        txn.setCurrency("USD");
        txn.setMerchantId("MER-QUERY");
        txn.setLocation("Test City");
        txn.setTimestamp(Instant.now().toEpochMilli());
        txn.setDeviceId("DEVICE-TEST");

        restTemplate.postForEntity("/api/transactions", txn, Map.class);

        // ── Wait for alert to be persisted ──
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(alertRepository.findAll().stream()
                    .anyMatch(a -> a.getAccountId().equals(uniqueAccount)))
                    .isTrue();
        });

        // ── Act: query alerts via REST API ──
        ResponseEntity<String> alertsResponse = restTemplate.getForEntity(
                "/api/alerts?accountId=" + uniqueAccount, String.class);

        // ── Assert ──
        assertThat(alertsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(alertsResponse.getBody()).contains(uniqueAccount);
        assertThat(alertsResponse.getBody()).contains("HIGH_AMOUNT");
    }
}
