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
public class DriverOfferSentEvent {

    public static final String TOPIC = "driver.offer.sent";

    private String rideId;
    private String driverId;
    private int attemptNumber;
    private int ttlSeconds;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant offeredAt;
}
