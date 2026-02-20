-- Add multi-tenancy support
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_trip_tenant ON trips(tenant_id);
