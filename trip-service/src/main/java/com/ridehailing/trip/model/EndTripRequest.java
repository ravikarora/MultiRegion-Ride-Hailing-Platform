package com.ridehailing.trip.model;

import lombok.Data;

@Data
public class EndTripRequest {
    private double endLat;
    private double endLng;
    private double distanceKm;
}
