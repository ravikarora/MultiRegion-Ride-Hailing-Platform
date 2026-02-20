package com.ridehailing.trip.entity;

import com.ridehailing.shared.enums.TripStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips",
        indexes = {
                @Index(name = "idx_trip_rider", columnList = "rider_id"),
                @Index(name = "idx_trip_driver", columnList = "driver_id"),
                @Index(name = "idx_trip_status", columnList = "status"),
                @Index(name = "idx_trip_tenant", columnList = "tenant_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dispatch_request_id")
    private UUID dispatchRequestId;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    @Column(name = "pickup_lat")
    private double pickupLat;

    @Column(name = "pickup_lng")
    private double pickupLng;

    @Column(name = "destination_lat")
    private double destinationLat;

    @Column(name = "destination_lng")
    private double destinationLng;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "distance_km", precision = 10, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "base_fare", precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "surge_multiplier")
    private double surgeMultiplier;

    @Column(name = "fare_amount", precision = 10, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "region_id")
    private String regionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
