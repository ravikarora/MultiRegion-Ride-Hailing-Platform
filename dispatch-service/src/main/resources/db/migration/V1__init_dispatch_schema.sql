-- Dispatch requests table
CREATE TABLE IF NOT EXISTS dispatch_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id         VARCHAR(64)  NOT NULL,
    pickup_lat       DOUBLE PRECISION NOT NULL,
    pickup_lng       DOUBLE PRECISION NOT NULL,
    destination_lat  DOUBLE PRECISION,
    destination_lng  DOUBLE PRECISION,
    tier             VARCHAR(20)  NOT NULL,
    payment_method   VARCHAR(20)  NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    idempotency_key  VARCHAR(128) UNIQUE,
    region_id        VARCHAR(64),
    assigned_driver_id VARCHAR(64),
    attempt_count    INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dispatch_rider   ON dispatch_requests(rider_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_status  ON dispatch_requests(status);
CREATE INDEX IF NOT EXISTS idx_dispatch_idem    ON dispatch_requests(idempotency_key);

-- Driver offers table
CREATE TABLE IF NOT EXISTS driver_offers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispatch_request_id UUID NOT NULL REFERENCES dispatch_requests(id),
    driver_id           VARCHAR(64) NOT NULL,
    attempt_number      INT NOT NULL,
    offered_at          TIMESTAMPTZ NOT NULL,
    responded_at        TIMESTAMPTZ,
    ttl_seconds         INT NOT NULL DEFAULT 15,
    response            VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_offer_dispatch ON driver_offers(dispatch_request_id);
CREATE INDEX IF NOT EXISTS idx_offer_driver   ON driver_offers(driver_id);

-- Geo cells for surge pricing
CREATE TABLE IF NOT EXISTS geo_cells (
    cell_id          VARCHAR(32)  PRIMARY KEY,
    region_id        VARCHAR(64),
    active_drivers   INT          NOT NULL DEFAULT 0,
    pending_rides    INT          NOT NULL DEFAULT 0,
    surge_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    computed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
