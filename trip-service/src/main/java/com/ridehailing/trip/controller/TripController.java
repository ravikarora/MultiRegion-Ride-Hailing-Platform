package com.ridehailing.trip.controller;

import com.ridehailing.shared.dto.ApiResponse;
import com.ridehailing.trip.entity.Trip;
import com.ridehailing.trip.model.EndTripRequest;
import com.ridehailing.trip.model.StartTripRequest;
import com.ridehailing.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<ApiResponse<Trip>> startTrip(
            @Valid @RequestBody StartTripRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        Trip trip = tripService.startTrip(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(trip));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<ApiResponse<Trip>> getTrip(@PathVariable("tripId") UUID tripId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getTrip(tripId)));
    }

    @PostMapping("/{tripId}/end")
    public ResponseEntity<ApiResponse<Trip>> endTrip(
            @PathVariable("tripId") UUID tripId,
            @RequestBody EndTripRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        return ResponseEntity.ok(ApiResponse.ok(tripService.endTrip(tripId, request)));
    }

    @PostMapping("/{tripId}/pause")
    public ResponseEntity<ApiResponse<Trip>> pauseTrip(@PathVariable("tripId") UUID tripId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.pauseTrip(tripId)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_STATE", ex.getMessage()));
    }
}
