CREATE TABLE IF NOT EXISTS payments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id        UUID NOT NULL,
    rider_id       VARCHAR(64) NOT NULL,
    amount         NUMERIC(10, 2) NOT NULL,
    currency       CHAR(3) DEFAULT 'USD',
    payment_method VARCHAR(20),
    psp_reference  VARCHAR(128),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    retry_count    INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_trip   ON payments(trip_id);
CREATE INDEX IF NOT EXISTS idx_payment_rider  ON payments(rider_id);
CREATE INDEX IF NOT EXISTS idx_payment_status ON payments(status);
