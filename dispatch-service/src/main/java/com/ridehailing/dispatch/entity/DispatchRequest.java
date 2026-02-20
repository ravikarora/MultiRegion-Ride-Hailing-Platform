package com.ridehailing.dispatch.entity;

import com.ridehailing.shared.enums.PaymentMethod;
import com.ridehailing.shared.enums.RideStatus;
import com.ridehailing.shared.enums.VehicleTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dispatch_requests",
        indexes = {
                @Index(name = "idx_dispatch_rider", columnList = "rider_id"),
                @Index(name = "idx_dispatch_status", columnList = "status"),
                @Index(name = "idx_dispatch_idempotency", columnList = "idempotency_key", unique = true),
                @Index(name = "idx_dispatch_tenant", columnList = "tenant_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DispatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic lock version — JPA increments this on every UPDATE.
     * If two drivers call acceptRide concurrently, the second UPDATE will see
     * a stale version and throw OptimisticLockException, preventing double-accept.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "destination_lat")
    private double destinationLat;

    @Column(name = "destination_lng")
    private double destinationLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "region_id")
    private String regionId;

    @Column(name = "assigned_driver_id")
    private String assignedDriverId;

    @Column(name = "attempt_count")
    private int attemptCount;

    @OneToMany(mappedBy = "dispatchRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DriverOffer> offers = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
