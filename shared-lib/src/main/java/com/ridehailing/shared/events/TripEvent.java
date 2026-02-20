package com.ridehailing.shared.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ridehailing.shared.enums.TripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripEvent {

    public static final String TOPIC_STARTED = "trip.started";
    public static final String TOPIC_ENDED   = "trip.ended";
    public static final String TOPIC_PAUSED  = "trip.paused";

    private String tripId;
    private String rideId;
    private String driverId;
    private String riderId;
    private String tenantId;
    private TripStatus status;
    private BigDecimal fareAmount;
    private double surgeMultiplier;
    private long durationSeconds;
    private double distanceKm;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant eventTime;
}
