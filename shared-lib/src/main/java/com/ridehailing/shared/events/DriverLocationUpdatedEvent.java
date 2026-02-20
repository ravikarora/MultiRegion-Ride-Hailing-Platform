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
public class DriverLocationUpdatedEvent {

    public static final String TOPIC = "driver.location.updated";

    private String driverId;
    private double latitude;
    private double longitude;
    private String regionId;
    private String status;
    private String tier;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
}
