package com.ridehailing.dispatch.service;

import com.ridehailing.dispatch.entity.DispatchRequest;
import com.ridehailing.dispatch.entity.DriverOffer;
import com.ridehailing.dispatch.exception.DispatchException;
import com.ridehailing.dispatch.model.DriverCandidate;
import com.ridehailing.dispatch.model.RideRequest;
import com.ridehailing.dispatch.model.RideResponse;
import com.ridehailing.dispatch.repository.DispatchRequestRepository;
import com.ridehailing.dispatch.repository.DriverOfferRepository;
import com.ridehailing.shared.enums.DriverStatus;
import com.ridehailing.shared.enums.OfferResponse;
import com.ridehailing.shared.enums.RideStatus;
import com.ridehailing.shared.events.DriverOfferSentEvent;
import com.ridehailing.shared.events.RideRequestedEvent;
import com.ridehailing.shared.events.RideStatusChangedEvent;
import com.ridehailing.dispatch.metrics.DispatchMetrics;
import com.ridehailing.shared.featureflag.FeatureFlagService;
import com.ridehailing.shared.util.IdempotencyUtil;
import com.ridehailing.shared.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core dispatch orchestration logic.
 *
 * Dispatch flow:
 *  1. Validate idempotency → return cached response if replay
 *  2. Persist DispatchRequest (PENDING)
 *  3. Find candidates via Redis GEORADIUS
 *  4. Acquire distributed lock per ride (prevent double-dispatch)
 *  5. Send offer to top candidate, record DriverOffer
 *  6. Set Redis offer-TTL key (15s); expiry triggers reassignment via scheduled job
 *  7. On accept/decline → update state, publish Kafka events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchOrchestrator {

    private static final int MAX_DISPATCH_ATTEMPTS = 3;
    private static final int OFFER_TTL_SECONDS = 15;
    private static final String LOCK_PREFIX = "lock:ride:";
    private static final String OFFER_TTL_KEY_PREFIX = "offer:ttl:";

    private final DispatchRequestRepository dispatchRequestRepository;
    private final DriverOfferRepository driverOfferRepository;
    private final DriverCandidateService candidateService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedissonClient redissonClient;
    private final FeatureFlagService featureFlagService;
    private final DispatchMetrics metrics;

    @Transactional
    public RideResponse createRide(RideRequest req, String idempotencyKey) {
        // Kill switch: ops can disable all new dispatches instantly via Redis flag
        String tenantId = req.getTenantId() != null ? req.getTenantId() : "default";
        if (featureFlagService.isEnabled(tenantId, FeatureFlagService.DISPATCH_KILL_SWITCH, false)) {
            metrics.recordKillSwitchRejection();
            throw new DispatchException("SERVICE_UNAVAILABLE",
                    "Dispatch is temporarily disabled for maintenance. Please try again shortly.");
        }

        // Idempotency check
        if (idempotencyKey != null) {
            Optional<DispatchRequest> existing = dispatchRequestRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key {}", idempotencyKey);
                metrics.recordIdempotentReplay();
                return toResponse(existing.get());
            }
        }

        DispatchRequest dispatch = DispatchRequest.builder()
                .riderId(req.getRiderId())
                .pickupLat(req.getPickupLat())
                .pickupLng(req.getPickupLng())
                .destinationLat(req.getDestinationLat())
                .destinationLng(req.getDestinationLng())
                .tier(req.getTier())
                .paymentMethod(req.getPaymentMethod())
                .status(RideStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .tenantId(req.getTenantId() != null ? req.getTenantId() : "default")
                .regionId(req.getRegionId())
                .attemptCount(0)
                .build();

        dispatch = dispatchRequestRepository.save(dispatch);
        long dispatchStartMs = System.currentTimeMillis();

        // Publish ride.requested event
        RideRequestedEvent event = RideRequestedEvent.builder()
                .rideId(dispatch.getId().toString())
                .riderId(req.getRiderId())
                .pickupLat(req.getPickupLat())
                .pickupLng(req.getPickupLng())
                .destinationLat(req.getDestinationLat())
                .destinationLng(req.getDestinationLng())
                .tier(req.getTier())
                .paymentMethod(req.getPaymentMethod())
                .regionId(req.getRegionId())
                .idempotencyKey(idempotencyKey)
                .requestedAt(Instant.now())
                .build();

        kafkaTemplate.send(KafkaTopics.RIDE_REQUESTED, dispatch.getId().toString(), event);

        // Async dispatch to first available driver
        dispatchToNextCandidate(dispatch, new HashSet<>());

        // Record latency and count
        long latencyMs = System.currentTimeMillis() - dispatchStartMs;
        metrics.getDispatchLatencyTimer().record(Duration.ofMillis(latencyMs));
        metrics.recordRideCreated();

        return toResponse(dispatch);
    }

    /**
     * Find next best candidate and send offer with distributed lock to prevent
     * concurrent double-dispatch on the same ride.
     */
    @Transactional
    public void dispatchToNextCandidate(DispatchRequest dispatch, Set<String> triedDriverIds) {
        UUID rideId = dispatch.getId();
        RLock lock = redissonClient.getLock(LOCK_PREFIX + rideId);

        try {
            boolean acquired = lock.tryLock(2, 5, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Could not acquire lock for ride {}, skipping dispatch attempt", rideId);
                return;
            }

            // Refresh from DB to get latest status
            DispatchRequest fresh = dispatchRequestRepository.findById(rideId)
                    .orElseThrow(() -> new DispatchException("RIDE_NOT_FOUND", "Ride " + rideId + " not found"));

            if (fresh.getStatus() == RideStatus.ACCEPTED || fresh.getStatus() == RideStatus.CANCELLED) {
                log.info("Ride {} already in terminal state {}, skipping dispatch", rideId, fresh.getStatus());
                return;
            }

            if (fresh.getAttemptCount() >= MAX_DISPATCH_ATTEMPTS) {
                markNoDriverFound(fresh);
                return;
            }

            List<DriverCandidate> candidates = candidateService.findCandidates(
                    fresh.getPickupLat(), fresh.getPickupLng(), fresh.getTier(),
                    triedDriverIds, fresh.getRegionId());

            if (candidates.isEmpty()) {
                markNoDriverFound(fresh);
                return;
            }

            DriverCandidate best = candidates.get(0);
            int attempt = fresh.getAttemptCount() + 1;

            // Record offer in DB
            DriverOffer offer = DriverOffer.builder()
                    .dispatchRequest(fresh)
                    .driverId(best.getDriverId())
                    .attemptNumber(attempt)
                    .offeredAt(Instant.now())
                    .ttlSeconds(OFFER_TTL_SECONDS)
                    .build();
            driverOfferRepository.save(offer);

            // Update ride status to DISPATCHING
            fresh.setStatus(RideStatus.DISPATCHING);
            fresh.setAttemptCount(attempt);
            dispatchRequestRepository.save(fresh);

            // Mark driver as DISPATCHING in Redis
            candidateService.markDriverStatus(best.getDriverId(), DriverStatus.DISPATCHING);

            // Set Redis TTL key for offer expiry (scheduler polls this)
            RLock offerTtlKey = redissonClient.getLock(OFFER_TTL_KEY_PREFIX + rideId + ":" + best.getDriverId());
            offerTtlKey.lock(OFFER_TTL_SECONDS, TimeUnit.SECONDS);

            // Publish driver.offer.sent
            DriverOfferSentEvent offerEvent = DriverOfferSentEvent.builder()
                    .rideId(rideId.toString())
                    .driverId(best.getDriverId())
                    .attemptNumber(attempt)
                    .ttlSeconds(OFFER_TTL_SECONDS)
                    .offeredAt(Instant.now())
                    .build();
            kafkaTemplate.send(KafkaTopics.DRIVER_OFFER_SENT, rideId.toString(), offerEvent);

            log.info("Offered ride {} to driver {} (attempt {}/{})", rideId, best.getDriverId(), attempt, MAX_DISPATCH_ATTEMPTS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring dispatch lock for ride {}", rideId, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public RideResponse acceptRide(UUID rideId, String driverId) {
        DispatchRequest dispatch = getDispatchOrThrow(rideId);
        validateActiveOffer(dispatch, driverId);

        dispatch.setStatus(RideStatus.ACCEPTED);
        dispatch.setAssignedDriverId(driverId);
        try {
            dispatchRequestRepository.saveAndFlush(dispatch);
        } catch (OptimisticLockException | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Another driver accepted concurrently — surface a clean conflict error
            throw new DispatchException("RIDE_ALREADY_ACCEPTED",
                    "Ride " + rideId + " was just accepted by another driver. Your offer is no longer valid.");
        }

        // Update offer record
        driverOfferRepository.findByDispatchRequestIdAndDriverIdAndResponseIsNull(rideId, driverId)
                .ifPresent(offer -> {
                    offer.setResponse(OfferResponse.ACCEPTED);
                    offer.setRespondedAt(Instant.now());
                    driverOfferRepository.save(offer);
                });

        candidateService.markDriverStatus(driverId, DriverStatus.ON_TRIP);

        publishRideStatusEvent(dispatch, RideStatus.ACCEPTED, driverId, null, KafkaTopics.RIDE_ACCEPTED);
        metrics.recordOfferAccepted();
        log.info("Ride {} accepted by driver {}", rideId, driverId);

        return toResponse(dispatch);
    }

    @Transactional
    public RideResponse declineRide(UUID rideId, String driverId) {
        DispatchRequest dispatch = getDispatchOrThrow(rideId);

        driverOfferRepository.findByDispatchRequestIdAndDriverIdAndResponseIsNull(rideId, driverId)
                .ifPresent(offer -> {
                    offer.setResponse(OfferResponse.DECLINED);
                    offer.setRespondedAt(Instant.now());
                    driverOfferRepository.save(offer);
                });

        candidateService.markDriverStatus(driverId, DriverStatus.IDLE);

        publishRideStatusEvent(dispatch, RideStatus.DISPATCHING, driverId, "DECLINED", KafkaTopics.RIDE_DECLINED);
        metrics.recordOfferDeclined();
        log.info("Ride {} declined by driver {}, reassigning", rideId, driverId);

        // Attempt reassignment
        Set<String> tried = new HashSet<>();
        tried.add(driverId);
        dispatchToNextCandidate(dispatch, tried);

        return toResponse(dispatch);
    }

    @Transactional
    public RideResponse markDriverArrived(UUID rideId, String driverId) {
        DispatchRequest dispatch = getDispatchOrThrow(rideId);
        if (dispatch.getStatus() != RideStatus.ACCEPTED) {
            throw new DispatchException("INVALID_STATE",
                    "Cannot mark arrival: ride is " + dispatch.getStatus() + ", expected ACCEPTED");
        }
        if (!driverId.equals(dispatch.getAssignedDriverId())) {
            throw new DispatchException("UNAUTHORIZED_DRIVER",
                    "Driver " + driverId + " is not the assigned driver for ride " + rideId);
        }
        dispatch.setStatus(RideStatus.DRIVER_ARRIVED);
        dispatchRequestRepository.save(dispatch);
        publishRideStatusEvent(dispatch, RideStatus.DRIVER_ARRIVED, driverId, null, KafkaTopics.RIDE_DRIVER_ARRIVED);
        log.info("Driver {} arrived for ride {}", driverId, rideId);
        return toResponse(dispatch);
    }

    @Transactional
    public RideResponse startRideInProgress(UUID rideId, String driverId) {
        DispatchRequest dispatch = getDispatchOrThrow(rideId);
        if (dispatch.getStatus() != RideStatus.DRIVER_ARRIVED) {
            throw new DispatchException("INVALID_STATE",
                    "Cannot start trip: ride is " + dispatch.getStatus() + ", expected DRIVER_ARRIVED");
        }
        if (!driverId.equals(dispatch.getAssignedDriverId())) {
            throw new DispatchException("UNAUTHORIZED_DRIVER",
                    "Driver " + driverId + " is not the assigned driver for ride " + rideId);
        }
        dispatch.setStatus(RideStatus.IN_PROGRESS);
        dispatchRequestRepository.save(dispatch);
        publishRideStatusEvent(dispatch, RideStatus.IN_PROGRESS, driverId, null, KafkaTopics.RIDE_IN_PROGRESS);
        log.info("Ride {} transitioned to IN_PROGRESS by driver {}", rideId, driverId);
        return toResponse(dispatch);
    }

    @Transactional
    public RideResponse cancelRide(UUID rideId, String requesterId) {
        DispatchRequest dispatch = getDispatchOrThrow(rideId);
        if (dispatch.getStatus() == RideStatus.IN_PROGRESS) {
            throw new DispatchException("CANNOT_CANCEL", "Cannot cancel a ride already in progress");
        }
        dispatch.setStatus(RideStatus.CANCELLED);
        dispatchRequestRepository.save(dispatch);
        publishRideStatusEvent(dispatch, RideStatus.CANCELLED, null, "USER_CANCELLED", KafkaTopics.RIDE_CANCELLED);
        return toResponse(dispatch);
    }

    public RideResponse getRide(UUID rideId) {
        return toResponse(getDispatchOrThrow(rideId));
    }

    // --- helpers ---

    private DispatchRequest getDispatchOrThrow(UUID rideId) {
        return dispatchRequestRepository.findById(rideId)
                .orElseThrow(() -> new DispatchException("RIDE_NOT_FOUND", "Ride " + rideId + " not found"));
    }

    private void validateActiveOffer(DispatchRequest dispatch, String driverId) {
        if (dispatch.getStatus() != RideStatus.DISPATCHING) {
            throw new DispatchException("INVALID_STATE", "Ride is not in DISPATCHING state");
        }
    }

    private void markNoDriverFound(DispatchRequest dispatch) {
        dispatch.setStatus(RideStatus.NO_DRIVER_FOUND);
        dispatchRequestRepository.save(dispatch);
        publishRideStatusEvent(dispatch, RideStatus.NO_DRIVER_FOUND, null, "NO_DRIVERS_AVAILABLE", KafkaTopics.RIDE_NO_DRIVER_FOUND);
        metrics.recordNoDriverFound();
        log.warn("No driver found for ride {}", dispatch.getId());
    }

    private void publishRideStatusEvent(DispatchRequest dispatch, RideStatus status,
                                        String driverId, String reason, String topic) {
        RideStatusChangedEvent event = RideStatusChangedEvent.builder()
                .rideId(dispatch.getId().toString())
                .riderId(dispatch.getRiderId())
                .driverId(driverId)
                .status(status)
                .reason(reason)
                .changedAt(Instant.now())
                .build();
        kafkaTemplate.send(topic, dispatch.getId().toString(), event);
    }

    private RideResponse toResponse(DispatchRequest d) {
        return RideResponse.builder()
                .rideId(d.getId())
                .riderId(d.getRiderId())
                .status(d.getStatus())
                .assignedDriverId(d.getAssignedDriverId())
                .tier(d.getTier())
                .estimatedSurgeMultiplier(1.0)
                .createdAt(d.getCreatedAt())
                .build();
    }
}
