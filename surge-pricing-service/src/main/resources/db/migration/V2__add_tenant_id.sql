-- Add multi-tenancy support
ALTER TABLE geo_cells
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'default';
