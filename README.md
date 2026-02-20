# Multi-Region Ride-Hailing Platform

A production-grade ride-hailing platform built with **Java 21 + Spring Boot 3.3**, implementing driver-rider matching, dynamic surge pricing, trip lifecycle management, and payments orchestration at scale.

---

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway :8080                        │
│   (tenant validation · per-tenant rate limiting · idempotency)  │
└─────┬──────────┬──────────┬──────────┬──────────┬──────────────┘
      │          │          │          │          │
  :8081      :8082      :8083      :8084      :8085   :8086
Driver      Dispatch   Trip       Surge      Payment  Notification
Location    Service    Service    Pricing    Service  Service
Service     (LLD)                Service    (CB+retry+outbox)
      │          │          │          │          │          │
      └──────────┴──────────┴──────────┴──────────┴──────────┘
                              │
              ┌───────────────┼───────────────┐
           Kafka           Redis           Postgres
        (event bus)    (geo+hot KV)      (transactions)
```

---

## Functional Requirements Status

| Requirement | Status | Notes |
|---|---|---|
| Real-time driver location ingestion (1–2 updates/s) | ✅ Working | `POST /api/v1/locations` → Redis GEOADD + Kafka. Driver metadata TTL 30s |
| Ride request flow (pickup, destination, tier, payment) | ✅ Working | `POST /api/v1/rides` with full field set and Idempotency-Key |
| Dispatch/Matching — p95 ≤ 1s, reassign on decline/timeout | ✅ Working | GEORADIUS → scoring → Redisson lock → 15s TTL → 3-attempt reassignment |
| Dynamic surge pricing — per geo-cell, supply/demand | ✅ Working | H3 resolution-8, 5-min sliding window, 3.0× cap, 10s snapshot cadence |
| Trip lifecycle — start, pause, end, fare, receipts | ✅ Working | All states implemented; fare = base + per-km + per-min × surge |
| Payments orchestration — PSP, retries, reconciliation | ✅ Working | Resilience4j CB + retry; Transactional Outbox; reconciliation every 5min |
| Notifications — push/SMS for key ride states | ✅ Working | All 9 state transitions wired to push/SMS stubs via Kafka |
| Admin/ops tooling — feature flags, kill-switches, observability | ✅ Working | Redis-backed per-tenant flags; Prometheus metrics; Kafka UI |

---

## Multi-Region Support

### Design vs Implementation

The platform is **designed** for multi-region and the data model is fully region-aware. All entities, request models, and Kafka events carry a `regionId` field. However, a **single-region deployment** is what runs locally — cross-region replication is documented in `docs/HLD.md` but not operationally wired in this codebase.

### What IS implemented (code is region-aware)

| Concern | Implementation |
|---|---|
| Redis geo index | **Region-scoped key**: `drivers:geo:{regionId}` — drivers in different regions never pollute each other |
| Supply/demand snapshots | `regionId` derived from each driver's location update and carried in the Kafka event |
| Dispatch candidate search | Queries `drivers:geo:{regionId}` — a ride in `ap-south-1` only searches drivers in that region |
| Surge pricing | `GeoCell` entity stores `regionId`; surge keys are per-H3-cell (cells are geographically isolated) |
| All DB tables | `tenant_id` + `region_id` columns on every table |
| API Gateway | Per-tenant rate limiting (200 rps standard / 1000 rps premium); `X-Tenant-ID` enforced on all routes |
| Feature flags | Per-tenant Redis hash: `HSET feature-flags:{tenantId} {flag} true/false` |

### What requires additional work for a true multi-region deployment

| Gap | What is needed |
|---|---|
| **Separate infrastructure per region** | Each region needs its own Kafka cluster, Redis cluster, and Postgres primary. The `docker-compose-infra.yml` runs one region only |
| **Cross-region reference data sync** | Tenant config and feature flags need async replication between regions (e.g., Kafka MirrorMaker or a global config service) |
| **Global rider/driver registry** | The `drivers` geo index and `dispatch_requests` table are region-local. A global read-replica or federation layer is needed for cross-region trip handoffs |
| **DNS-level routing** | GeoDNS or a global load balancer (AWS Route53 latency routing, Cloudflare) to route requests to the nearest region |
| **Kafka MirrorMaker** | For replicating critical topics (e.g., `payment.captured`) to a global audit region |

### How to test multi-region locally

Run two instances with different `regionId` environment variables. Drivers registered with `regionId=ap-south-1` will only appear for dispatches in `ap-south-1`, and drivers registered with `regionId=us-east-1` will only appear for dispatches in `us-east-1`:

```bash
# Register a driver in ap-south-1
curl -X POST http://localhost:8081/api/v1/locations \
  -H 'Content-Type: application/json' \
  -d '{"driverId":"drv_india","latitude":12.97,"longitude":77.59,"status":"IDLE","tier":"ECONOMY","rating":4.8,"regionId":"ap-south-1"}'

# Register a driver in us-east-1
curl -X POST http://localhost:8081/api/v1/locations \
  -H 'Content-Type: application/json' \
  -d '{"driverId":"drv_ny","latitude":40.71,"longitude":-74.00,"status":"IDLE","tier":"ECONOMY","rating":4.5,"regionId":"us-east-1"}'

# A ride request in ap-south-1 will ONLY match drv_india
curl -X POST http://localhost:8082/api/v1/rides \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: ik-region-test' \
  -d '{"riderId":"usr_1","pickupLat":12.97,"pickupLng":77.59,"destinationLat":12.93,"destinationLng":77.62,"tier":"ECONOMY","paymentMethod":"CARD","regionId":"ap-south-1"}'
```

---

## Prerequisites

- Docker 24+ and Docker Compose v2
- Java 21 (LTS) and Maven 3.9+ (for local development only)

---

## Quick Start — Infrastructure + Native Services

```bash
# 1. Start Kafka, Redis, Postgres via Docker
docker compose -f docker-compose-infra.yml up -d

# 2. Build all services
mvn clean package -DskipTests

# 3. Run services (each in a separate terminal)
java -jar driver-location-service/target/driver-location-service-*.jar
java -jar dispatch-service/target/dispatch-service-*.jar
java -jar trip-service/target/trip-service-*.jar
java -jar surge-pricing-service/target/surge-pricing-service-*.jar
java -jar payment-service/target/payment-service-*.jar
java -jar notification-service/target/notification-service-*.jar
java -jar api-gateway/target/api-gateway-*.jar
```

---

## Service Ports

| Service | Port | URL |
|---------|------|-----|
| API Gateway | 8080 | http://localhost:8080 |
| Driver Location Service | 8081 | http://localhost:8081 |
| Dispatch Service | 8082 | http://localhost:8082 |
| Trip Service | 8083 | http://localhost:8083 |
| Surge Pricing Service | 8084 | http://localhost:8084 |
| Payment Service | 8085 | http://localhost:8085 |
| Notification Service | 8086 | http://localhost:8086 |
| Kafka UI | 9095 | http://localhost:9095 |
| Redis | 6379 | redis://localhost:6379 |
| Postgres | 5432 | jdbc:postgresql://localhost:5432/ridehailing (user: ridehailing / pass: ridehailing) |

---

## Database Connection

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `ridehailing` |
| Username | `ridehailing` |
| Password | `ridehailing` |
| Tables | `dispatch_requests`, `driver_offers`, `trips`, `payments`, `payment_outbox`, `geo_cells` |

---

## End-to-End Scenario (curl)

### Step 1 — Register a driver location

```bash
curl -X POST http://localhost:8081/api/v1/locations \
  -H 'Content-Type: application/json' \
  -d '{
    "driverId": "drv_001",
    "latitude": 12.9716,
    "longitude": 77.5946,
    "status": "IDLE",
    "tier": "ECONOMY",
    "rating": 4.7,
    "regionId": "ap-south-1"
  }'
```

### Step 2 — Rider creates a ride request

```bash
curl -X POST http://localhost:8082/api/v1/rides \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: ik-ride-001' \
  -d '{
    "riderId": "usr_101",
    "pickupLat": 12.9716,
    "pickupLng": 77.5946,
    "destinationLat": 12.9352,
    "destinationLng": 77.6245,
    "tier": "ECONOMY",
    "paymentMethod": "CARD",
    "regionId": "ap-south-1"
  }'
# Note the rideId from the response
```

### Step 3 — Driver accepts

```bash
curl -X POST "http://localhost:8082/api/v1/rides/{rideId}/accept?driverId=drv_001" \
  -H 'Idempotency-Key: ik-accept-001'
```

### Step 4 — Mark driver arrived

```bash
curl -X POST "http://localhost:8082/api/v1/rides/{rideId}/driver-arrived?driverId=drv_001"
```

### Step 5 — Start trip

```bash
curl -X POST http://localhost:8083/api/v1/trips \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: ik-trip-001' \
  -d '{
    "dispatchRequestId": "{rideId}",
    "driverId": "drv_001",
    "riderId": "usr_101",
    "pickupLat": 12.9716,
    "pickupLng": 77.5946,
    "destinationLat": 12.9352,
    "destinationLng": 77.6245,
    "regionId": "ap-south-1",
    "tenantId": "default"
  }'
# Note the tripId from the response
```

### Step 6 — End trip (triggers fare calculation + payment)

```bash
curl -X POST http://localhost:8083/api/v1/trips/{tripId}/end \
  -H 'Content-Type: application/json' \
  -d '{"endLat": 12.9352, "endLng": 77.6245, "distanceKm": 8.4}'
# Response includes fareAmount and surgeMultiplier
# Kafka event trip.ended → payment-service → CAPTURED within ~2s
```

### Step 7 — Check surge for a location

```bash
curl "http://localhost:8084/api/v1/surge?lat=12.9716&lng=77.5946"
```

### Step 8 — Check payments table

```bash
PGPASSWORD=ridehailing psql -h localhost -U ridehailing -d ridehailing \
  -c "SELECT id, trip_id, amount, status, tenant_id FROM payments ORDER BY created_at DESC LIMIT 5;"
```

---

## Admin / Ops

### Feature Flags (toggle at runtime, no restart needed)

```bash
# Disable dispatch globally
redis-cli HSET feature-flags:default dispatch_kill_switch true

# Re-enable
redis-cli HSET feature-flags:default dispatch_kill_switch false

# Disable surge pricing for a specific tenant
redis-cli HSET feature-flags:tenant-acme surge_pricing_enabled false

# Enable the alternative scoring algorithm (A/B test)
redis-cli HSET feature-flags:default new_scoring_algo true

# Disable auto payment charge (manual review mode)
redis-cli HSET feature-flags:default auto_payment_charge false
```

### Available Feature Flags

| Flag | Default | Effect |
|---|---|---|
| `dispatch_kill_switch` | `false` | When `true`, all new dispatches return 503 immediately |
| `surge_pricing_enabled` | `true` | When `false`, all surge multipliers return 1.0 |
| `auto_payment_charge` | `true` | When `false`, payments queue for manual review |
| `new_scoring_algo` | `false` | When `true`, uses balanced α=0.4/β=0.4 weights (A/B test) |
| `real_time_tracking` | `true` | Real-time driver location tracking toggle |

### Observability

```bash
# Prometheus metrics
curl http://localhost:8082/actuator/prometheus | grep dispatch

# Circuit breaker state (payment PSP)
curl http://localhost:8085/actuator/circuitbreakers

# Kafka topics and consumer lag
open http://localhost:9095

# Health checks
curl http://localhost:8082/actuator/health
```

---

## Running Tests

```bash
# All tests (unit + integration)
mvn test

# Specific service only
mvn -pl dispatch-service test
mvn -pl trip-service test
mvn -pl surge-pricing-service test
```

### Test Coverage

| Test | Type | What it verifies |
|---|---|---|
| `DispatchScoringTest` | Unit | Scoring formula: closer/higher-rated/lower-decline-rate driver wins |
| `SurgeCalculatorServiceTest` | Unit | Surge formula: floor=1.0, cap=3.0, recency weighting, sliding window |
| `FareCalculatorServiceTest` | Unit | Fare = base + per-km + per-min × surge; 2dp rounding |
| `DispatchIntegrationTest` | Integration | Given driver in Redis → ride requested → `driver.offer.sent` published to embedded Kafka |

---

## Project Structure

```
ride-hailing-platform/
├── pom.xml                      # Root Maven multi-module POM (Java 21)
├── docker-compose-infra.yml     # Infrastructure only (Kafka, Redis, Postgres, Kafka UI)
├── shared-lib/                  # Shared DTOs, events, enums, utilities
│   └── src/main/java/com/ridehailing/shared/
│       ├── events/              # Kafka event POJOs (TripEvent, PaymentEvent, ...)
│       ├── dto/                 # ApiResponse, GeoPoint
│       ├── enums/               # DriverStatus, RideStatus, VehicleTier, ...
│       ├── featureflag/         # FeatureFlagService (Redis-backed, per-tenant)
│       └── util/                # H3Util, KafkaTopics constants
├── api-gateway/                 # Spring Cloud Gateway :8080
│   └── filter/                  # TenantValidationFilter, IdempotencyFilter, LoggingFilter
├── driver-location-service/     # Location ingestion → Redis (region-scoped) + Kafka :8081
├── dispatch-service/            # Core LLD: matching engine :8082
│   ├── service/DispatchOrchestrator    # Full ride lifecycle: PENDING→DISPATCHING→ACCEPTED
│   ├── service/DriverCandidateService  # GEORADIUS + scoring (region-scoped)
│   └── service/OfferTimeoutScheduler   # Reassignment on timeout
├── trip-service/                # Trip lifecycle + fare calculation :8083
├── surge-pricing-service/       # H3 supply/demand → multiplier :8084
├── payment-service/             # PSP stub + Resilience4j + Outbox :8085
│   ├── service/PaymentOrchestrator     # Transactional outbox pattern
│   ├── service/OutboxPublisher         # Polls outbox → Kafka (at-least-once)
│   └── service/PaymentReconciliationJob # Retries FAILED/stale PENDING
├── notification-service/        # Push/SMS stubs via Kafka consumers :8086
└── docs/
    ├── HLD.md                   # High-Level Design + capacity math + multi-region diagram
    ├── LLD-Dispatch.md          # Low-Level Design: dispatch algorithm + latency budget
    ├── API-Events.md            # REST API + Kafka event schemas
    ├── DataModel-ERD.md         # ERD for all services
    └── Resilience-Plan.md       # CB, retries, backpressure, failure modes
```

---

## Key Design Decisions

### Dispatch Algorithm
1. Redis `GEORADIUS` on `drivers:geo:{regionId}` — region-scoped, 5km radius, limit 50
2. Filter: `status=IDLE AND tier >= requested tier`
3. Score: `0.5·(1/distance) + 0.3·rating + 0.2·(1/declineRate)` — A/B testable via feature flag
4. Redisson distributed lock on `lock:ride:{id}` prevents concurrent double-dispatch
5. JPA `@Version` optimistic locking on `dispatch_requests` prevents concurrent double-accept
6. 15s offer TTL, scheduler reassigns to next candidate (max 3 attempts)

### Surge Pricing
- H3 resolution 8 cells (~0.74 km²) for fine-grained geo pricing
- 5-minute sliding window with recency weighting (recent snapshots weighted higher)
- `surge = clamp(1.0 + (weightedDemandRatio − 1.0) × 0.5, 1.0, 3.0)`
- Redis cache with 10s TTL (matches snapshot cadence); Postgres for audit

### Payments — Transactional Outbox Pattern
```
Trip ends → DB transaction atomically writes:
  1. payments row (PENDING)
  2. payment_outbox row (PENDING)
→ OutboxPublisher polls every 500ms → Kafka → marks PUBLISHED
→ PSP charge runs async — trip completion NEVER blocked by PSP latency
→ Circuit Breaker + 3 retries if PSP fails
→ ReconciliationJob retries FAILED payments every 5min
```

### Multi-Tenancy
- `tenant_id` column on all tables; `X-Tenant-ID` header required on all API calls
- Per-tenant rate limits enforced at API Gateway (200 rps / 1000 rps premium)
- Feature flags keyed per-tenant: `feature-flags:{tenantId}`
- Kafka partitioning by tenant+region for isolation

### Idempotency
All POST endpoints accept `Idempotency-Key` header. Enforced at API Gateway (missing key on POST = HTTP 400) except high-frequency location updates (last-write-wins is safe).

### Resilience
- Resilience4j Circuit Breaker on PSP gateway (opens at 50% failure rate, 10s wait)
- Exponential backoff retry on payments (1s → 2s → 4s)
- Kafka manual offset commit (`AckMode.MANUAL_IMMEDIATE`) with `max.poll.records=50`
- Redis TTL-based offer expiry with fallback scheduler (every 5s)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 — virtual threads (Project Loom) |
| Framework | Spring Boot 3.3, Spring Cloud Gateway |
| Events | Apache Kafka 7.6 (Confluent) |
| Hot KV / Geo | Redis 7.2 |
| Transactions | PostgreSQL 15 |
| Distributed Locks | Redisson 3.27 |
| Resilience | Resilience4j 2.2 (CB + Retry) |
| Geo Indexing | Uber H3 4.1 (resolution 8 for surge, 9 for dispatch) |
| Metrics | Micrometer + Prometheus |
| Build | Maven 3.9 multi-module |
| Container | Docker + Docker Compose |
| Testing | JUnit 5, Mockito, Spring Kafka Test (EmbeddedKafka), H2 |

---

## Compliance Notes

- **PCI**: No raw card data stored; PSP handles tokenisation
- **GDPR/DPDP**: `DELETE /riders/{id}` would hash-replace PII fields (right to erasure)
- **Observability**: All services expose `/actuator/prometheus` and `/actuator/health`
