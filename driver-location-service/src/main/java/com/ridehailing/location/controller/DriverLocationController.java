package com.ridehailing.location.controller;

import com.ridehailing.location.model.LocationUpdateRequest;
import com.ridehailing.location.service.DriverLocationService;
import com.ridehailing.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DriverLocationController {

    private final DriverLocationService locationService;

    /**
     * Driver location update — called 1-2x per second per online driver.
     * Idempotency-Key header accepted but not strictly required for location updates
     * (last-write-wins is safe here).
     */
    @PostMapping("/locations")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @Valid @RequestBody LocationUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        locationService.updateLocation(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/drivers/{driverId}/meta")
    public ResponseEntity<ApiResponse<Map<Object, Object>>> getDriverMeta(
            @PathVariable("driverId") String driverId) {
        Map<Object, Object> meta = locationService.getDriverMeta(driverId);
        if (meta.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(meta));
    }
}
