# 🔍 Real-Time Fraud Detection Engine

A Spring Boot + Kafka application that ingests financial transactions in real time, evaluates them against configurable fraud detection rules, and persists alerts to PostgreSQL for analyst review.

Built as a portfolio project demonstrating event-driven architecture, enterprise error handling, and production-quality testing.

---

## Architecture

```
┌─────────────────┐    ┌─────────────────────┐    ┌──────────────────┐
│   REST Client   │───>│  Transaction API    │───>│  Kafka Producer  │
│ (Postman/curl)  │    │  POST /api/txns     │    │  (idempotent)    │
└─────────────────┘    └─────────────────────┘    └────────┬─────────┘
                                                           │
                                                           ▼
                                                  ┌────────────────┐
                                                  │  Kafka Topic   │
                                                  │ "transactions" │
                                                  └────────┬───────┘
                                                           │
                                                           ▼
┌──────────────────┐    ┌─────────────────────┐    ┌───────────────┐
│   PostgreSQL     │<───│  Alert Service      │<───│ Kafka Consumer│
│  (fraud_alerts)  │    │  (idempotent save)  │    │ + Fraud Rules │
└──────────────────┘    └─────────────────────┘    └───────┬───────┘
                                                           │
                                                           ▼ (on failure)
                                                  ┌────────────────────┐
                                                  │  Dead Letter Topic │
                                                  │ "transactions.DLT" │
                                                  └────────────────────┘
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| **Message key = `accountId`** | All transactions for the same account go to the same Kafka partition, guaranteeing per-account ordering |
| **Idempotent producer** (`enable.idempotence=true`) | Prevents duplicate messages on network retries — critical in financial systems |
| **`acks=all`** | Strongest durability guarantee: no message loss even if a broker crashes |
| **DLQ (Dead Letter Queue)** | Failed messages are routed to a separate topic instead of blocking the consumer — "poison pill" protection |
| **Strategy pattern for rules** | New fraud rules are added by creating a single class — zero changes to existing code (Open/Closed Principle) |
| **Idempotent persistence** | Same transaction + rule combo is saved only once, even if the consumer reprocesses a message |

---

## Tech Stack

| Component | Technology | Version |
|---|---|---|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.3.4 |
| Messaging | Apache Kafka (Spring Kafka) | KRaft mode via Confluent 7.7.0 |
| Database | PostgreSQL | 16 |
| Caching | Caffeine | (managed by Spring Boot) |
| Testing | JUnit 5, AssertJ, Awaitility, **Testcontainers** | 1.20.1 |
| Infrastructure | Docker Compose | — |

---

## Prerequisites

- **Java 21** (or 17+)
- **Maven 3.9+**
- **Docker** (for Kafka, PostgreSQL, and integration tests)

---

## Quick Start

### 1. Start infrastructure
```bash
docker-compose up -d
```
Wait for Kafka and PostgreSQL to become healthy:
```bash
docker-compose ps   # Both should show "healthy"
```

### 2. Build & run
```bash
mvn clean package -DskipTests
java -jar target/fraud-detection-engine-1.0.0-SNAPSHOT.jar
```

### 3. Simulate transactions
```bash
# Generate 20 demo transactions (mix of normal and suspicious)
curl -X POST "http://localhost:8081/api/transactions/simulate?count=20"
```

### 4. Check alerts
```bash
# List all fraud alerts
curl http://localhost:8081/api/alerts

# Filter by account
curl "http://localhost:8081/api/alerts?accountId=ACC-001"

# Filter by status
curl "http://localhost:8081/api/alerts?status=OPEN"
```

### 5. Submit a custom transaction
```bash
curl -X POST http://localhost:8081/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-007",
    "amount": 50000.00,
    "currency": "USD",
    "merchantId": "MER-SUSPECT",
    "location": "New York",
    "deviceId": "DEVICE-NEW"
  }'
```

### 6. Update alert status (analyst workflow)
```bash
curl -X PATCH http://localhost:8081/api/alerts/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "REVIEWED"}'
```

---

## Fraud Rules

| Rule | Trigger Condition | Configurable Via |
|---|---|---|
| **HIGH_AMOUNT** | Transaction amount > $10,000 | `fraud.rules.high-amount-threshold` |
| **VELOCITY** | > 5 transactions from the same account within 5 minutes | `fraud.rules.velocity-max-transactions`, `fraud.rules.velocity-window-seconds` |

To add a new rule, create a class implementing `FraudRule` and annotate it with `@Component`. Spring auto-discovers it — no other code changes needed.

---

## Running Tests

```bash
# Unit tests only (fast, no Docker needed)
mvn test

# All tests including Testcontainers integration (requires Docker)
mvn verify
```

### Test Coverage

| Test | Type | What It Proves |
|---|---|---|
| `FraudRuleEngineTest` | Unit | Business logic correctness — threshold boundaries, velocity tracking, multi-rule triggering |
| `FraudDetectionIntegrationTest` | Integration (Testcontainers) | Full pipeline works: REST → Kafka → Consumer → DB with real infrastructure |

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/transactions` | Submit a transaction for fraud evaluation (returns `202 Accepted`) |
| `POST` | `/api/transactions/simulate?count=N` | Generate N demo transactions |
| `GET` | `/api/alerts` | List alerts (supports `?accountId=`, `?status=`, `?page=`, `?size=`) |
| `PATCH` | `/api/alerts/{id}/status` | Update alert status. Body: `{"status": "REVIEWED"}` |
| `GET` | `/actuator/health` | Application health check |

---

## Project Structure

```
src/main/java/com/fraudengine/
├── FraudDetectionApplication.java    # Entry point
├── config/
│   └── KafkaConfig.java              # DLQ error handler
├── controller/
│   ├── TransactionController.java    # Transaction ingestion API
│   ├── AlertController.java          # Alert query & management API
│   ├── HomeController.java           # Root endpoint (API directory)
│   └── GlobalExceptionHandler.java   # Consistent error responses
├── model/
│   ├── Transaction.java              # DTO with validation
│   └── FraudAlert.java               # JPA entity with status workflow
├── producer/
│   └── TransactionProducer.java      # Kafka publisher (keyed by accountId)
├── consumer/
│   └── TransactionConsumer.java      # Kafka listener + rule evaluation
├── service/
│   ├── FraudRule.java                # Strategy interface
│   ├── HighAmountRule.java           # Amount threshold rule
│   ├── VelocityRule.java             # Transaction frequency rule
│   ├── FraudRuleEngine.java          # Rule orchestrator
│   └── AlertService.java             # Alert persistence (idempotent)
└── repository/
    └── FraudAlertRepository.java     # Spring Data JPA
```

---

## What I'd Do Differently at Scale

| Concern | Current Approach | Production Approach |
|---|---|---|
| **Serialization** | JSON (simple, readable) | Avro + Schema Registry (schema evolution, smaller payloads, backward compatibility) |
| **Velocity tracking** | Caffeine in-memory cache | Redis or Kafka Streams state store (shared across multiple consumer instances) |
| **Stream processing** | Spring `@KafkaListener` | Kafka Streams DSL (windowed aggregations, stream-stream joins, exactly-once semantics) |
| **Security** | None (demo) | Spring Security + JWT for analyst API, mTLS for inter-service communication |
| **Observability** | Spring Actuator | Micrometer → Prometheus → Grafana dashboards (consumer lag, alert rates, processing latency p99) |
| **Deployment** | Docker Compose | Kubernetes with consumer group auto-scaling based on partition count |
| **Fraud rules** | Hardcoded rules | Drools or a rules engine with hot-reload, supplemented by ML anomaly detection |
| **Schema** | Hibernate DDL auto | Flyway migrations for production-safe schema evolution |

---

## License

This project is for portfolio/educational purposes.
