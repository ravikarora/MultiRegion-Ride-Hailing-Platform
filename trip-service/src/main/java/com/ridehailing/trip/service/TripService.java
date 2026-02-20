package com.ridehailing.trip.service;

import com.ridehailing.shared.enums.TripStatus;
import com.ridehailing.shared.events.TripEvent;
import com.ridehailing.shared.util.H3Util;
import com.ridehailing.shared.util.KafkaTopics;
import com.ridehailing.trip.entity.Trip;
import com.ridehailing.trip.model.EndTripRequest;
import com.ridehailing.trip.model.StartTripRequest;
import com.ridehailing.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private static final String SURGE_KEY_PREFIX = "surge:cell:";

    private final TripRepository tripRepository;
    private final FareCalculatorService fareCalculator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public Trip startTrip(StartTripRequest req, String idempotencyKey) {
        // Idempotency: if trip for this dispatch already started, return it
        if (req.getDispatchRequestId() != null) {
            tripRepository.findByDispatchRequestId(req.getDispatchRequestId())
                    .ifPresent(existing -> {
                        if (existing.getStatus() == TripStatus.STARTED) {
                            log.info("Idempotent start trip for dispatch {}", req.getDispatchRequestId());
                        }
                    });
        }

        Trip trip = Trip.builder()
                .dispatchRequestId(req.getDispatchRequestId())
                .driverId(req.getDriverId())
                .riderId(req.getRiderId())
                .status(TripStatus.STARTED)
                .pickupLat(req.getPickupLat())
                .pickupLng(req.getPickupLng())
                .destinationLat(req.getDestinationLat())
                .destinationLng(req.getDestinationLng())
                .startedAt(Instant.now())
                .surgeMultiplier(1.0)
                .currency("USD")
                .tenantId(req.getTenantId() != null ? req.getTenantId() : "default")
                .regionId(req.getRegionId())
                .build();

        trip = tripRepository.save(trip);

        kafkaTemplate.send(KafkaTopics.TRIP_STARTED, trip.getId().toString(), TripEvent.builder()
                .tripId(trip.getId().toString())
                .rideId(trip.getDispatchRequestId() != null ? trip.getDispatchRequestId().toString() : null)
                .driverId(trip.getDriverId())
                .riderId(trip.getRiderId())
                .status(TripStatus.STARTED)
                .eventTime(Instant.now())
                .build());

        log.info("Trip {} started for rider {} with driver {}", trip.getId(), req.getRiderId(), req.getDriverId());
        return trip;
    }

    @Transactional
    public Trip endTrip(UUID tripId, EndTripRequest req) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip " + tripId + " not found"));

        if (trip.getStatus() != TripStatus.STARTED && trip.getStatus() != TripStatus.PAUSED) {
            throw new IllegalStateException("Trip is not in an active state: " + trip.getStatus());
        }

        Instant endedAt = Instant.now();
        long durationSeconds = ChronoUnit.SECONDS.between(trip.getStartedAt(), endedAt);
        double distanceKm = req.getDistanceKm() > 0 ? req.getDistanceKm() : 1.0;

        // Look up surge multiplier from Redis
        double surge = getSurgeForTrip(trip);

        BigDecimal baseFare = fareCalculator.baseFare(distanceKm, durationSeconds);
        BigDecimal totalFare = fareCalculator.calculate(distanceKm, durationSeconds, surge);

        trip.setStatus(TripStatus.ENDED);
        trip.setEndedAt(endedAt);
        trip.setDistanceKm(BigDecimal.valueOf(distanceKm));
        trip.setBaseFare(baseFare);
        trip.setSurgeMultiplier(surge);
        trip.setFareAmount(totalFare);
        trip = tripRepository.save(trip);

        kafkaTemplate.send(KafkaTopics.TRIP_ENDED, trip.getId().toString(), TripEvent.builder()
                .tripId(trip.getId().toString())
                .rideId(trip.getDispatchRequestId() != null ? trip.getDispatchRequestId().toString() : null)
                .driverId(trip.getDriverId())
                .riderId(trip.getRiderId())
                .tenantId(trip.getTenantId() != null ? trip.getTenantId() : "default")
                .status(TripStatus.ENDED)
                .fareAmount(totalFare)
                .surgeMultiplier(surge)
                .durationSeconds(durationSeconds)
                .distanceKm(distanceKm)
                .eventTime(endedAt)
                .build());

        log.info("Trip {} ended: dist={}km duration={}s fare={} (surge={})",
                tripId, distanceKm, durationSeconds, totalFare, surge);
        return trip;
    }

    @Transactional
    public Trip pauseTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip " + tripId + " not found"));
        trip.setStatus(TripStatus.PAUSED);
        return tripRepository.save(trip);
    }

    public Trip getTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip " + tripId + " not found"));
    }

    private double getSurgeForTrip(Trip trip) {
        // Derive the H3 cell from the trip's actual pickup coordinates
        String cellId = H3Util.surgeCell(trip.getPickupLat(), trip.getPickupLng());
        String surgeKey = SURGE_KEY_PREFIX + cellId;
        String cached = redisTemplate.opsForValue().get(surgeKey);
        if (cached != null) {
            try {
                double multiplier = Double.parseDouble(cached);
                log.debug("Surge lookup: cell={} multiplier={}", cellId, multiplier);
                return multiplier;
            } catch (NumberFormatException ignored) {}
        }
        // Fallback: try the region-level default if per-cell cache miss
        String regionKey = SURGE_KEY_PREFIX + (trip.getRegionId() != null ? trip.getRegionId() : "default");
        String regionCached = redisTemplate.opsForValue().get(regionKey);
        if (regionCached != null) {
            try { return Double.parseDouble(regionCached); } catch (NumberFormatException ignored) {}
        }
        log.debug("No surge cache hit for cell={}, defaulting to 1.0", cellId);
        return 1.0;
    }
}
