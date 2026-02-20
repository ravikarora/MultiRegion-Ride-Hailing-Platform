-- Optimistic locking: JPA @Version field prevents double-accept race condition.
-- When two drivers call acceptRide concurrently, the second UPDATE sees a stale
-- version and throws OptimisticLockException — no driver can be double-assigned.
ALTER TABLE dispatch_requests
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
