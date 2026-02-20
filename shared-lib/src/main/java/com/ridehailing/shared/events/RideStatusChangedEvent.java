package com.ridehailing.shared.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ridehailing.shared.enums.RideStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideStatusChangedEvent {

    public static final String TOPIC_ACCEPTED  = "ride.accepted";
    public static final String TOPIC_DECLINED  = "ride.declined";
    public static final String TOPIC_CANCELLED = "ride.cancelled";
    public static final String TOPIC_NO_DRIVER = "ride.no_driver_found";

    private String rideId;
    private String riderId;
    private String driverId;
    private RideStatus status;
    private String reason;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant changedAt;
}
