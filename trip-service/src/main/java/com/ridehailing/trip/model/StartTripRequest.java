package com.ridehailing.trip.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StartTripRequest {

    @NotNull
    private UUID dispatchRequestId;

    @NotBlank
    private String driverId;

    @NotBlank
    private String riderId;

    private double pickupLat;
    private double pickupLng;
    private double destinationLat;
    private double destinationLng;
    private String regionId = "default";
    private String tenantId = "default";
}
