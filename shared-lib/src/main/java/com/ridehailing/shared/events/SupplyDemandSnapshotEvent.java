package com.ridehailing.shared.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyDemandSnapshotEvent {

    public static final String TOPIC = "supply.demand.snapshot";

    private String geoCell;
    private String regionId;
    private int activeDrivers;
    private int pendingRides;
    private double demandMultiplier;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant computedAt;
}
