# Data Model — ERD (Dispatch Component)

## Entity Relationship Diagram

```mermaid
erDiagram
    DRIVERS {
        UUID id PK
        VARCHAR name
        VARCHAR tier
        FLOAT rating
        VARCHAR region_id
        TIMESTAMPTZ created_at
    }

    RIDERS {
        UUID id PK
        VARCHAR name
        VARCHAR phone_hash "PII encrypted SHA-256"
        VARCHAR email_hash "PII encrypted"
        VARCHAR region_id
        TIMESTAMPTZ created_at
    }

    DISPATCH_REQUESTS {
        UUID id PK
        VARCHAR rider_id FK
        FLOAT pickup_lat
        FLOAT pickup_lng
        FLOAT destination_lat
        FLOAT destination_lng
        VARCHAR tier
        VARCHAR payment_method
        VARCHAR status
        VARCHAR idempotency_key "UNIQUE"
        VARCHAR region_id
        VARCHAR assigned_driver_id
        INT attempt_count
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    DRIVER_OFFERS {
        UUID id PK
        UUID dispatch_request_id FK
        VARCHAR driver_id
        INT attempt_number
        TIMESTAMPTZ offered_at
        TIMESTAMPTZ responded_at
        INT ttl_seconds
        VARCHAR response "ACCEPTED/DECLINED/TIMEOUT"
        TIMESTAMPTZ created_at
    }

    TRIPS {
        UUID id PK
        UUID dispatch_request_id FK
        VARCHAR driver_id
        VARCHAR rider_id
        VARCHAR status
        FLOAT pickup_lat
        FLOAT pickup_lng
        FLOAT destination_lat
        FLOAT destination_lng
        TIMESTAMPTZ started_at
        TIMESTAMPTZ ended_at
        NUMERIC distance_km
        NUMERIC base_fare
        FLOAT surge_multiplier
        NUMERIC fare_amount
        CHAR currency
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    PAYMENTS {
        UUID id PK
        UUID trip_id FK
        VARCHAR rider_id
        NUMERIC amount
        CHAR currency
        VARCHAR payment_method
        VARCHAR psp_reference
        VARCHAR status
        TEXT failure_reason
        INT retry_count
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    GEO_CELLS {
        VARCHAR cell_id PK "H3 cell address"
        VARCHAR region_id
        INT active_drivers
        INT pending_rides
        FLOAT surge_multiplier
        TIMESTAMPTZ computed_at
    }

    RIDERS ||--o{ DISPATCH_REQUESTS : "creates"
    DISPATCH_REQUESTS ||--o{ DRIVER_OFFERS : "generates"
    DISPATCH_REQUESTS ||--o| TRIPS : "leads to"
    TRIPS ||--o| PAYMENTS : "triggers"
```

---

## Table Descriptions

### `dispatch_requests`
Central table for the ride request lifecycle. The `idempotency_key` unique constraint ensures exactly-once semantics for all POST /rides requests.

**Status transitions:**
`PENDING → DISPATCHING → ACCEPTED → IN_PROGRESS → COMPLETED`
`PENDING → CANCELLED`
`DISPATCHING → NO_DRIVER_FOUND`

**Indexes:**
- `idx_dispatch_rider` on `rider_id` — history lookups
- `idx_dispatch_status` on `status` — filter active rides
- `idx_dispatch_idempotency` UNIQUE on `idempotency_key`

---

### `driver_offers`
Append-only audit trail. Every offer sent creates a new row. The `response` column is NULL until the driver responds or times out. This allows full replay of the dispatch history.

**Indexes:**
- `idx_offer_dispatch` on `dispatch_request_id` — join to dispatch_requests
- `idx_offer_driver` on `driver_id` — driver history / decline rate calculation

---

### `trips`
Created when driver physically starts the trip. Stores fare breakdown separately (`base_fare` without surge, `fare_amount` = base × surge) for dispute resolution.

**Indexes:**
- `idx_trip_rider` on `rider_id`
- `idx_trip_driver` on `driver_id`
- `idx_trip_status` on `status`

---

### `payments`
One payment row per trip. `retry_count` tracks how many PSP attempts were made. `psp_reference` is the external payment provider's transaction ID for reconciliation.

---

### `geo_cells`
Snapshot table updated every 10s by Surge Pricing Service. `cell_id` is an H3 resolution-7 hex address (e.g. `872a1072fffffff`). The surge multiplier stored here drives fare calculation and is cached in Redis.

---

## PII Handling (GDPR/DPDP Compliance)

- `riders.phone_hash` — SHA-256 of E.164 phone number; raw number NOT stored in SQL
- `riders.email_hash` — SHA-256 of lowercase email
- Raw PII stored only in encrypted KV store (HashiCorp Vault / AWS KMS) keyed by `rider_id`
- Audit logs strip PII before writing to Kafka / Elasticsearch

---

## Migration Strategy (Flyway)

Each service owns its Flyway migration scripts in `src/main/resources/db/migration/`:
- `V1__init_dispatch_schema.sql` — initial tables
- `V2__add_region_index.sql` — add region-based partitioning index (future)

Flyway runs at service startup; `ddl-auto: validate` prevents Hibernate from auto-altering schema.
