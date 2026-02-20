CREATE TABLE IF NOT EXISTS trips (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispatch_request_id UUID,
    driver_id           VARCHAR(64) NOT NULL,
    rider_id            VARCHAR(64) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    pickup_lat          DOUBLE PRECISION,
    pickup_lng          DOUBLE PRECISION,
    destination_lat     DOUBLE PRECISION,
    destination_lng     DOUBLE PRECISION,
    started_at          TIMESTAMPTZ,
    ended_at            TIMESTAMPTZ,
    distance_km         NUMERIC(10, 3),
    base_fare           NUMERIC(10, 2),
    surge_multiplier    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    fare_amount         NUMERIC(10, 2),
    currency            CHAR(3) DEFAULT 'USD',
    region_id           VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trip_rider  ON trips(rider_id);
CREATE INDEX IF NOT EXISTS idx_trip_driver ON trips(driver_id);
CREATE INDEX IF NOT EXISTS idx_trip_status ON trips(status);
