package com.ridehailing.surge.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Custom Micrometer metrics for the Surge Pricing Service.
 *
 * Metrics at /actuator/prometheus:
 *   surge_snapshots_processed_total     — number of supply/demand snapshots consumed
 *   surge_flag_disabled_total           — times surge returned 1.0 due to feature flag
 *   surge_multiplier_current{cell}      — current multiplier for tracked high-demand cells
 */
@Component
public class SurgeMetrics {

    private final Counter snapshotsProcessed;
    private final Counter flagDisabledCounter;
    private final AtomicReference<Double> currentMaxMultiplier = new AtomicReference<>(1.0);

    public SurgeMetrics(MeterRegistry registry) {
        this.snapshotsProcessed = Counter.builder("surge.snapshots.processed")
                .description("Number of supply/demand snapshots processed")
                .register(registry);

        this.flagDisabledCounter = Counter.builder("surge.flag_disabled")
                .description("Times surge returned 1.0 because surge_pricing_enabled=false")
                .register(registry);

        // Gauge tracks the current maximum surge multiplier across all cells
        Gauge.builder("surge.multiplier.max", currentMaxMultiplier, AtomicReference::get)
                .description("Highest current surge multiplier across all geo-cells")
                .register(registry);
    }

    public void recordSnapshotProcessed()          { snapshotsProcessed.increment(); }
    public void recordFlagDisabled()               { flagDisabledCounter.increment(); }
    public void updateMaxMultiplier(double value)  { currentMaxMultiplier.set(value); }
}
