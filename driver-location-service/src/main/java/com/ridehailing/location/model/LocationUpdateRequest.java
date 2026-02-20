package com.ridehailing.location.model;

import com.ridehailing.shared.enums.DriverStatus;
import com.ridehailing.shared.enums.VehicleTier;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocationUpdateRequest {

    @NotBlank
    private String driverId;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @NotNull
    private DriverStatus status;

    @NotNull
    private VehicleTier tier;

    private double rating = 4.5;
    private String regionId = "default";
}
