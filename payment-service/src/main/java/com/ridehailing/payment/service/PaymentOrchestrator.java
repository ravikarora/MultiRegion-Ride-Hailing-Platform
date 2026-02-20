package com.ridehailing.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.payment.entity.Payment;
import com.ridehailing.payment.entity.PaymentOutbox;
import com.ridehailing.payment.entity.PaymentOutbox.OutboxStatus;
import com.ridehailing.payment.repository.PaymentOutboxRepository;
import com.ridehailing.payment.repository.PaymentRepository;
import com.ridehailing.shared.events.PaymentEvent;
import com.ridehailing.shared.events.TripEvent;
import com.ridehailing.payment.metrics.PaymentMetrics;
import com.ridehailing.shared.featureflag.FeatureFlagService;
import com.ridehailing.shared.util.KafkaTopics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment orchestration implementing the Transactional Outbox Pattern.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Old approach (dual-write problem):                             │
 * │    1. INSERT payments row  ──┐                                  │
 * │    2. kafkaTemplate.send()   │  ← if Kafka send fails after     │
 * │                              │    DB commit, event is lost      │
 * │  New approach (outbox):                                         │
 * │    ┌──── Single DB Transaction ────────────────────────────┐    │
 * │    │  1. INSERT payments row                               │    │
 * │    │  2. INSERT payment_outbox row (status=PENDING)        │    │
 * │    └───────────────────────────────────────────────────────┘    │
 * │    3. OutboxPublisher polls → publishes → marks PUBLISHED       │
 * │                                                                 │
 * │  Result: trip completion NEVER blocked by PSP latency.          │
 * │          Events guaranteed at-least-once via outbox relay.      │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * PSP charge runs async (after Kafka event is published by OutboxPublisher).
 * Resilience4j CB + retry applies to the PSP call only, not the DB write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrchestrator {

    private static final int MAX_OUTBOX_RETRIES = 5;

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final PspGateway pspGateway;
    private final ObjectMapper objectMapper;
    private final FeatureFlagService featureFlagService;
    private final PaymentMetrics paymentMetrics;

    /**
     * Called when a trip.ended Kafka event is consumed.
     * Atomically creates the Payment row AND the outbox entry.
     * Returns immediately — PSP call is decoupled.
     */
    @Transactional
    public void initiatePayment(TripEvent tripEvent) {
        UUID tripId = UUID.fromString(tripEvent.getTripId());

        if (paymentRepository.findByTripId(tripId).isPresent()) {
            log.info("Idempotent: payment already exists for trip {}", tripId);
            return;
        }

        String tenantId = tripEvent.getTenantId() != null ? tripEvent.getTenantId() : "default";

        Payment payment = Payment.builder()
                .tripId(tripId)
                .riderId(tripEvent.getRiderId())
                .amount(tripEvent.getFareAmount())
                .currency("USD")
                .paymentMethod("CARD")
                .tenantId(tenantId)
                .status("PENDING")
                .retryCount(0)
                .build();
        payment = paymentRepository.save(payment);

        PaymentEvent initiatedEvent = PaymentEvent.builder()
                .paymentId(payment.getId().toString())
                .tripId(tripEvent.getTripId())
                .riderId(tripEvent.getRiderId())
                .amount(tripEvent.getFareAmount())
                .currency("USD")
                .status("PENDING")
                .eventTime(Instant.now())
                .build();

        // Write outbox entry in the SAME transaction — atomic with payment insert
        writeOutbox(payment.getId(), tenantId, KafkaTopics.PAYMENT_INITIATED, initiatedEvent);

        paymentMetrics.recordInitiated();
        log.info("Payment {} created for trip {} (async PSP charge pending)", payment.getId(), tripId);

        // Feature flag: auto_payment_charge can be disabled for manual-review mode
        if (!featureFlagService.isEnabled(tenantId, FeatureFlagService.AUTO_PAYMENT_CHARGE, true)) {
            log.info("Auto payment charge disabled by feature flag for tenant={}, payment {} queued for manual review",
                    tenantId, payment.getId());
            return;
        }

        // Kick off async PSP charge
        final Payment savedPayment = payment;
        chargeAsync(savedPayment, tripEvent, tenantId);
    }

    /**
     * PSP charge — isolated from the payment creation transaction.
     * If this fails, the outbox entry for PAYMENT_INITIATED already exists.
     * A separate OutboxPublisher ensures that event still gets published.
     */
    @CircuitBreaker(name = "psp-gateway", fallbackMethod = "paymentFallback")
    @Retry(name = "psp-retry")
    @Transactional
    public void chargeAsync(Payment payment, TripEvent tripEvent, String tenantId) {
        PspGateway.PspResponse response = pspGateway.charge(
                payment.getRiderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod()
        );

        payment.setStatus("CAPTURED");
        payment.setPspReference(response.reference());
        paymentRepository.save(payment);

        PaymentEvent capturedEvent = PaymentEvent.builder()
                .paymentId(payment.getId().toString())
                .tripId(payment.getTripId().toString())
                .riderId(payment.getRiderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .pspReference(response.reference())
                .status("CAPTURED")
                .eventTime(Instant.now())
                .build();

        writeOutbox(payment.getId(), tenantId, KafkaTopics.PAYMENT_CAPTURED, capturedEvent);

        paymentMetrics.recordCaptured();
        log.info("Payment {} CAPTURED for trip {} psp_ref={}", payment.getId(), payment.getTripId(), response.reference());
    }

    public void paymentFallback(Payment payment, TripEvent tripEvent, String tenantId, Exception ex) {
        paymentMetrics.recordFailed();
        log.error("PSP charge failed for trip {} after retries: {}", payment.getTripId(), ex.getMessage());
        payment.setStatus("FAILED");
        payment.setFailureReason(ex.getMessage());
        payment.setRetryCount(payment.getRetryCount() + 1);
        paymentRepository.save(payment);

        PaymentEvent failedEvent = PaymentEvent.builder()
                .paymentId(payment.getId().toString())
                .tripId(payment.getTripId().toString())
                .riderId(payment.getRiderId())
                .amount(payment.getAmount())
                .status("FAILED")
                .failureReason(ex.getMessage())
                .eventTime(Instant.now())
                .build();

        writeOutbox(payment.getId(), tenantId, KafkaTopics.PAYMENT_FAILED, failedEvent);
    }

    // --- Outbox helpers ---

    private void writeOutbox(UUID paymentId, String tenantId, String eventType, PaymentEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            PaymentOutbox entry = PaymentOutbox.builder()
                    .paymentId(paymentId)
                    .tenantId(tenantId)
                    .eventType(eventType)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxRepository.save(entry);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise outbox payload for paymentId={}: {}", paymentId, e.getMessage(), e);
            throw new RuntimeException("Outbox serialisation failed", e);
        }
    }
}
