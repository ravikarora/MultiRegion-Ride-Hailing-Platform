CREATE TABLE IF NOT EXISTS geo_cells (
    cell_id          VARCHAR(32)  PRIMARY KEY,
    region_id        VARCHAR(64),
    active_drivers   INT          NOT NULL DEFAULT 0,
    pending_rides    INT          NOT NULL DEFAULT 0,
    surge_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    computed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
