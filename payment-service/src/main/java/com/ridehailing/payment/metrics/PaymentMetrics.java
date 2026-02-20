package com.ridehailing.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Micrometer metrics for the Payment Service.
 *
 * Metrics at /actuator/prometheus:
 *   payment_initiated_total
 *   payment_captured_total
 *   payment_failed_total
 *   payment_reconciled_total
 *   payment_pending_gauge       — current count of PENDING payments (staleness signal)
 *   payment_psp_latency_seconds — p50/p95/p99 histogram for PSP charge latency
 */
@Component
public class PaymentMetrics {

    private final Counter initiatedCounter;
    private final Counter capturedCounter;
    private final Counter failedCounter;
    private final Counter reconciledCounter;
    private final AtomicInteger pendingGauge = new AtomicInteger(0);
    private final Timer pspLatencyTimer;

    public PaymentMetrics(MeterRegistry registry) {
        this.initiatedCounter = Counter.builder("payment.initiated")
                .description("Total payments initiated")
                .register(registry);

        this.capturedCounter = Counter.builder("payment.captured")
                .description("Total payments successfully captured by PSP")
                .register(registry);

        this.failedCounter = Counter.builder("payment.failed")
                .description("Total payments that failed after all retries")
                .register(registry);

        this.reconciledCounter = Counter.builder("payment.reconciled")
                .description("Total payments recovered by the reconciliation job")
                .register(registry);

        Gauge.builder("payment.pending", pendingGauge, AtomicInteger::get)
                .description("Current number of payments in PENDING state (staleness signal)")
                .register(registry);

        this.pspLatencyTimer = Timer.builder("payment.psp.latency")
                .description("PSP charge call latency including retries")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(50))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry);
    }

    public void recordInitiated()                 { initiatedCounter.increment(); pendingGauge.incrementAndGet(); }
    public void recordCaptured()                  { capturedCounter.increment();  pendingGauge.decrementAndGet(); }
    public void recordFailed()                    { failedCounter.increment();    pendingGauge.decrementAndGet(); }
    public void recordReconciled()                { reconciledCounter.increment(); }
    public Timer getPspLatencyTimer()             { return pspLatencyTimer; }
}
