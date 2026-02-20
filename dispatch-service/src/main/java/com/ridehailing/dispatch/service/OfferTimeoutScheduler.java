package com.ridehailing.dispatch.service;

import com.ridehailing.dispatch.entity.DispatchRequest;
import com.ridehailing.dispatch.entity.DriverOffer;
import com.ridehailing.dispatch.metrics.DispatchMetrics;
import com.ridehailing.dispatch.repository.DispatchRequestRepository;
import com.ridehailing.dispatch.repository.DriverOfferRepository;
import com.ridehailing.shared.enums.OfferResponse;
import com.ridehailing.shared.enums.RideStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Polls for expired driver offers every 5 seconds and triggers reassignment.
 * In production this would be event-driven via Redis keyspace notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferTimeoutScheduler {

    private final DriverOfferRepository driverOfferRepository;
    private final DispatchRequestRepository dispatchRequestRepository;
    private final DispatchOrchestrator orchestrator;
    private final DispatchMetrics metrics;

    @Scheduled(fixedDelayString = "${dispatch.offer.timeout-check-ms:5000}")
    @Transactional
    public void checkExpiredOffers() {
        List<DispatchRequest> dispatching = dispatchRequestRepository
                .findAll()
                .stream()
                .filter(d -> d.getStatus() == RideStatus.DISPATCHING)
                .toList();

        for (DispatchRequest dispatch : dispatching) {
            List<DriverOffer> openOffers = driverOfferRepository
                    .findByDispatchRequestIdOrderByAttemptNumberDesc(dispatch.getId())
                    .stream()
                    .filter(o -> o.getResponse() == null)
                    .toList();

            for (DriverOffer offer : openOffers) {
                long secondsElapsed = ChronoUnit.SECONDS.between(offer.getOfferedAt(), Instant.now());
                if (secondsElapsed >= offer.getTtlSeconds()) {
                    offer.setResponse(OfferResponse.TIMEOUT);
                    offer.setRespondedAt(Instant.now());
                    driverOfferRepository.save(offer);

                    metrics.recordOfferTimeout();
                    log.info("Offer to driver {} for ride {} timed out after {}s, reassigning",
                            offer.getDriverId(), dispatch.getId(), secondsElapsed);

                    Set<String> tried = new HashSet<>();
                    tried.add(offer.getDriverId());
                    orchestrator.dispatchToNextCandidate(dispatch, tried);
                }
            }
        }
    }
}
