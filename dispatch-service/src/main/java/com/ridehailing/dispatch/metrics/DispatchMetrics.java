package com.ridehailing.dispatch.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Custom Micrometer metrics for the Dispatch Service.
 *
 * Metrics exposed at /actuator/prometheus:
 *
 *   dispatch_ride_requests_total{status="created|rejected|duplicate"}
 *   dispatch_latency_seconds{quantile="0.5|0.95|0.99"}   — time from request to offer sent
 *   dispatch_offer_response_total{outcome="accepted|declined|timeout"}
 *   dispatch_no_driver_found_total
 *   dispatch_kill_switch_rejections_total
 */
@Component
public class DispatchMetrics {

    private final Counter rideCreatedCounter;
    private final Counter rideRejectedCounter;
    private final Counter idempotentReplayCounter;
    private final Counter offerAcceptedCounter;
    private final Counter offerDeclinedCounter;
    private final Counter offerTimeoutCounter;
    private final Counter noDriverFoundCounter;
    private final Counter killSwitchCounter;
    private final Timer   dispatchLatencyTimer;

    public DispatchMetrics(MeterRegistry registry) {
        this.rideCreatedCounter = Counter.builder("dispatch.ride.requests")
                .tag("status", "created")
                .description("Total ride requests successfully created")
                .register(registry);

        this.rideRejectedCounter = Counter.builder("dispatch.ride.requests")
                .tag("status", "rejected")
                .description("Total ride requests rejected (validation, kill switch)")
                .register(registry);

        this.idempotentReplayCounter = Counter.builder("dispatch.ride.requests")
                .tag("status", "duplicate")
                .description("Idempotent replay requests (same key)")
                .register(registry);

        this.offerAcceptedCounter = Counter.builder("dispatch.offer.response")
                .tag("outcome", "accepted")
                .description("Driver offers accepted")
                .register(registry);

        this.offerDeclinedCounter = Counter.builder("dispatch.offer.response")
                .tag("outcome", "declined")
                .description("Driver offers declined")
                .register(registry);

        this.offerTimeoutCounter = Counter.builder("dispatch.offer.response")
                .tag("outcome", "timeout")
                .description("Driver offers timed out")
                .register(registry);

        this.noDriverFoundCounter = Counter.builder("dispatch.no_driver_found")
                .description("Rides where no driver was found after all attempts")
                .register(registry);

        this.killSwitchCounter = Counter.builder("dispatch.kill_switch_rejections")
                .description("Requests rejected because dispatch kill switch was active")
                .register(registry);

        // p50, p95, p99 histogram published to Prometheus
        this.dispatchLatencyTimer = Timer.builder("dispatch.latency")
                .description("Time from ride request received to offer sent to first driver (ms)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);
    }

    public void recordRideCreated()          { rideCreatedCounter.increment(); }
    public void recordRideRejected()         { rideRejectedCounter.increment(); }
    public void recordIdempotentReplay()     { idempotentReplayCounter.increment(); }
    public void recordOfferAccepted()        { offerAcceptedCounter.increment(); }
    public void recordOfferDeclined()        { offerDeclinedCounter.increment(); }
    public void recordOfferTimeout()         { offerTimeoutCounter.increment(); }
    public void recordNoDriverFound()        { noDriverFoundCounter.increment(); }
    public void recordKillSwitchRejection()  { killSwitchCounter.increment(); }
    public Timer getDispatchLatencyTimer()   { return dispatchLatencyTimer; }
}
