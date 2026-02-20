package com.ridehailing.dispatch.model;

import com.ridehailing.shared.enums.RideStatus;
import com.ridehailing.shared.enums.VehicleTier;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RideResponse {
    private UUID rideId;
    private String riderId;
    private RideStatus status;
    private String assignedDriverId;
    private VehicleTier tier;
    private double estimatedSurgeMultiplier;
    private Instant createdAt;
}
