package com.ridehailing.surge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "geo_cells")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "cellId")
public class GeoCell {

    @Id
    @Column(name = "cell_id", length = 32)
    private String cellId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "region_id")
    private String regionId;

    @Column(name = "active_drivers")
    private int activeDrivers;

    @Column(name = "pending_rides")
    private int pendingRides;

    @Column(name = "surge_multiplier")
    private double surgeMultiplier;

    @Column(name = "computed_at")
    private Instant computedAt;
}
