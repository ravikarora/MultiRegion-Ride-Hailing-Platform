-- Add multi-tenancy support
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_payment_tenant ON payments(tenant_id);

-- Outbox table for reliable async payment event publishing (Transactional Outbox Pattern)
-- Written atomically with the payment row; polled by OutboxPublisher every 500ms
CREATE TABLE IF NOT EXISTS payment_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID NOT NULL REFERENCES payments(id),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    event_type      VARCHAR(64) NOT NULL,   -- e.g. PAYMENT_INITIATED, PAYMENT_CAPTURED
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PUBLISHED | FAILED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_outbox_status     ON payment_outbox(status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON payment_outbox(created_at);
