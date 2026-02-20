package com.ridehailing.dispatch.controller;

import com.ridehailing.dispatch.exception.DispatchException;
import com.ridehailing.dispatch.model.RideRequest;
import com.ridehailing.dispatch.model.RideResponse;
import com.ridehailing.dispatch.service.DispatchOrchestrator;
import com.ridehailing.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<ApiResponse<RideResponse>> createRide(
            @Valid @RequestBody RideRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        RideResponse response = orchestrator.createRide(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<RideResponse>> getRide(@PathVariable("rideId") UUID rideId) {
        return ResponseEntity.ok(ApiResponse.ok(orchestrator.getRide(rideId)));
    }

    @PostMapping("/{rideId}/accept")
    public ResponseEntity<ApiResponse<RideResponse>> acceptRide(
            @PathVariable("rideId") UUID rideId,
            @RequestParam("driverId") String driverId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        return ResponseEntity.ok(ApiResponse.ok(orchestrator.acceptRide(rideId, driverId)));
    }

    @PostMapping("/{rideId}/decline")
    public ResponseEntity<ApiResponse<RideResponse>> declineRide(
            @PathVariable("rideId") UUID rideId,
            @RequestParam("driverId") String driverId) {

        return ResponseEntity.ok(ApiResponse.ok(orchestrator.declineRide(rideId, driverId)));
    }

    @PostMapping("/{rideId}/driver-arrived")
    public ResponseEntity<ApiResponse<RideResponse>> driverArrived(
            @PathVariable("rideId") UUID rideId,
            @RequestParam("driverId") String driverId) {

        return ResponseEntity.ok(ApiResponse.ok(orchestrator.markDriverArrived(rideId, driverId)));
    }

    @PostMapping("/{rideId}/start")
    public ResponseEntity<ApiResponse<RideResponse>> startRide(
            @PathVariable("rideId") UUID rideId,
            @RequestParam("driverId") String driverId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        return ResponseEntity.ok(ApiResponse.ok(orchestrator.startRideInProgress(rideId, driverId)));
    }

    @PostMapping("/{rideId}/cancel")
    public ResponseEntity<ApiResponse<RideResponse>> cancelRide(
            @PathVariable("rideId") UUID rideId,
            @RequestParam("requesterId") String requesterId) {

        return ResponseEntity.ok(ApiResponse.ok(orchestrator.cancelRide(rideId, requesterId)));
    }

    @ExceptionHandler(DispatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleDispatchException(DispatchException ex) {
        log.warn("Dispatch error [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }
}
