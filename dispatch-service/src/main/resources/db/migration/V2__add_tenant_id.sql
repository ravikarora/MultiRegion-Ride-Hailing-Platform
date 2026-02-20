-- Add multi-tenancy support
ALTER TABLE dispatch_requests
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_dispatch_tenant ON dispatch_requests(tenant_id);

-- Also add tenant_id to geo_cells (managed by surge-pricing-service but created here too)
ALTER TABLE geo_cells
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'default';
