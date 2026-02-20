package com.ridehailing.dispatch.entity;

import com.ridehailing.shared.enums.OfferResponse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_offers",
        indexes = {
                @Index(name = "idx_offer_dispatch", columnList = "dispatch_request_id"),
                @Index(name = "idx_offer_driver", columnList = "driver_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DriverOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatch_request_id", nullable = false)
    private DispatchRequest dispatchRequest;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "ttl_seconds")
    private int ttlSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "response")
    private OfferResponse response;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
