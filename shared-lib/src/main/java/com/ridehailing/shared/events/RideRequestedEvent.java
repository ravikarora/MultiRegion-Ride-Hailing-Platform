package com.ridehailing.shared.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ridehailing.shared.enums.PaymentMethod;
import com.ridehailing.shared.enums.VehicleTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestedEvent {

    public static final String TOPIC = "ride.requested";

    private String rideId;
    private String riderId;
    private double pickupLat;
    private double pickupLng;
    private double destinationLat;
    private double destinationLng;
    private VehicleTier tier;
    private PaymentMethod paymentMethod;
    private String regionId;
    private String idempotencyKey;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant requestedAt;
}
