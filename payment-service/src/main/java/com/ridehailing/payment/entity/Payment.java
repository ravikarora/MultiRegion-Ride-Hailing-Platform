package com.ridehailing.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_payment_trip", columnList = "trip_id"),
                @Index(name = "idx_payment_rider", columnList = "rider_id"),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_tenant", columnList = "tenant_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "psp_reference")
    private String pspReference;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "retry_count")
    private int retryCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
