package com.ridehailing.dispatch.model;

import com.ridehailing.shared.enums.PaymentMethod;
import com.ridehailing.shared.enums.VehicleTier;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RideRequest {

    @NotBlank
    private String riderId;

    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double pickupLat;

    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double pickupLng;

    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double destinationLat;

    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double destinationLng;

    @NotNull
    private VehicleTier tier;

    @NotNull
    private PaymentMethod paymentMethod;

    private String regionId = "default";

    private String tenantId = "default";
}
