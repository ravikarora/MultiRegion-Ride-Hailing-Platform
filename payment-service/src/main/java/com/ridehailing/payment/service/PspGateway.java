package com.ridehailing.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stub PSP (Payment Service Provider) gateway.
 * In production this would call Stripe, Adyen, Razorpay, etc.
 * Simulates occasional failures (20% failure rate) to test resilience.
 */
@Slf4j
@Component
public class PspGateway {

    public PspResponse charge(String riderId, BigDecimal amount, String currency, String paymentMethod) {
        log.info("PSP charge: rider={} amount={} {} method={}", riderId, amount, currency, paymentMethod);

        // Simulate PSP latency
        simulateLatency();

        // Simulate 20% failure rate
        if (Math.random() < 0.20) {
            throw new PspException("PSP_TIMEOUT", "Payment gateway timeout");
        }

        return PspResponse.builder()
                .reference("PSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status("CAPTURED")
                .build();
    }

    public PspResponse refund(String pspReference, BigDecimal amount) {
        log.info("PSP refund: ref={} amount={}", pspReference, amount);
        simulateLatency();
        return PspResponse.builder()
                .reference("REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status("REFUNDED")
                .build();
    }

    private void simulateLatency() {
        try {
            Thread.sleep((long) (Math.random() * 200 + 100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record PspResponse(String reference, String status) {
        public static PspResponseBuilder builder() {
            return new PspResponseBuilder();
        }

        public static class PspResponseBuilder {
            private String reference;
            private String status;

            public PspResponseBuilder reference(String reference) {
                this.reference = reference;
                return this;
            }

            public PspResponseBuilder status(String status) {
                this.status = status;
                return this;
            }

            public PspResponse build() {
                return new PspResponse(reference, status);
            }
        }
    }

    public static class PspException extends RuntimeException {
        private final String code;

        public PspException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
