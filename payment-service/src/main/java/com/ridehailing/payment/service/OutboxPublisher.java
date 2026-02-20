package com.ridehailing.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.payment.entity.PaymentOutbox;
import com.ridehailing.payment.entity.PaymentOutbox.OutboxStatus;
import com.ridehailing.payment.repository.PaymentOutboxRepository;
import com.ridehailing.shared.events.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional Outbox relay — polls payment_outbox every 500ms and publishes
 * PENDING entries to Kafka.
 *
 * Why this is safe:
 *   - Outbox entries are written in the same DB transaction as the Payment row.
 *   - If Kafka send fails, entry stays PENDING and will be retried next cycle.
 *   - After MAX_RETRIES failures the entry is marked FAILED for manual review.
 *   - In production this could be replaced with Debezium CDC for lower latency.
 *
 * Ordering guarantee:
 *   - Entries are processed in createdAt ASC order per poll cycle.
 *   - Kafka key = paymentId → same payment events go to same partition → ordered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int MAX_RETRIES = 5;

    private final PaymentOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 500) // poll every 500ms
    @Transactional
    public void publishPendingEntries() {
        List<PaymentOutbox> pending = outboxRepository.findPendingBatch();
        if (pending.isEmpty()) return;

        log.debug("OutboxPublisher: processing {} pending entries", pending.size());

        for (PaymentOutbox entry : pending) {
            try {
                PaymentEvent event = objectMapper.readValue(entry.getPayload(), PaymentEvent.class);

                // Synchronous send with get() to detect failures before marking PUBLISHED
                kafkaTemplate.send(entry.getEventType(), entry.getPaymentId().toString(), event).get();

                entry.setStatus(OutboxStatus.PUBLISHED);
                entry.setPublishedAt(Instant.now());
                outboxRepository.save(entry);

                log.info("Outbox: published eventType={} paymentId={}", entry.getEventType(), entry.getPaymentId());

            } catch (Exception e) {
                int retries = entry.getRetryCount() + 1;
                entry.setRetryCount(retries);

                if (retries >= MAX_RETRIES) {
                    entry.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox: permanently failed after {} retries paymentId={} error={}",
                            retries, entry.getPaymentId(), e.getMessage());
                } else {
                    log.warn("Outbox: retry {}/{} for paymentId={}: {}",
                            retries, MAX_RETRIES, entry.getPaymentId(), e.getMessage());
                }
                outboxRepository.save(entry);
            }
        }
    }
}
