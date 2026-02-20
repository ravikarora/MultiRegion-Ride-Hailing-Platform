package com.ridehailing.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox entry.
 *
 * Written atomically with the Payment row in the same DB transaction.
 * The OutboxPublisher polls this table every 500ms, publishes to Kafka,
 * then marks the entry PUBLISHED. This guarantees at-least-once delivery
 * without a dual-write race between the DB commit and Kafka send.
 *
 * Lifecycle: PENDING → PUBLISHED (happy path)
 *            PENDING → FAILED (after MAX_RETRY attempts)
 */
@Entity
@Table(name = "payment_outbox",
        indexes = {
                @Index(name = "idx_outbox_status",     columnList = "status"),
                @Index(name = "idx_outbox_created_at", columnList = "created_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PaymentOutbox {

    public enum OutboxStatus { PENDING, PUBLISHED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON-serialised PaymentEvent

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;
}
