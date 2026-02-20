package com.ridehailing.dispatch.integration;

import com.ridehailing.dispatch.model.DriverCandidate;
import com.ridehailing.dispatch.model.RideRequest;
import com.ridehailing.dispatch.service.DispatchOrchestrator;
import com.ridehailing.dispatch.service.DriverCandidateService;
import com.ridehailing.shared.enums.PaymentMethod;
import com.ridehailing.shared.enums.VehicleTier;
import com.ridehailing.shared.events.DriverOfferSentEvent;
import com.ridehailing.shared.featureflag.FeatureFlagService;
import com.ridehailing.shared.util.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test: verifies the full dispatch flow through real JPA and embedded Kafka.
 *
 * Scenario:
 *   Given  — a mock driver "drv_001" returned by DriverCandidateService
 *   When   — a ride is requested via DispatchOrchestrator.createRide()
 *   Then   — a driver.offer.sent message is published to Kafka with the correct driverId
 */
@SpringBootTest(classes = {
        com.ridehailing.dispatch.DispatchServiceApplication.class,
        TestKafkaConfig.class
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaTopics.DRIVER_OFFER_SENT,
                KafkaTopics.RIDE_REQUESTED
        }
)
@DirtiesContext
class DispatchIntegrationTest {

    // ── Infrastructure mocks — replace real Redis / Redisson ─────────────────
    @MockBean private DriverCandidateService driverCandidateService;
    @MockBean private RedissonClient redissonClient;
    @MockBean private FeatureFlagService featureFlagService;

    @Autowired private DispatchOrchestrator orchestrator;

    /** Kafka messages land here — the test reads from this queue. */
    private static final LinkedBlockingQueue<DriverOfferSentEvent> offerMessages =
            new LinkedBlockingQueue<>();

    @KafkaListener(
            topics = KafkaTopics.DRIVER_OFFER_SENT,
            groupId = "dispatch-integration-test"
    )
    void captureOfferSent(DriverOfferSentEvent event) {
        offerMessages.add(event);
    }

    @Test
    @Timeout(10)
    @DisplayName("Given driver in Redis, when ride requested, then driver.offer.sent is published")
    void givenDriverAvailable_whenRideRequested_thenOfferSentToKafka() throws InterruptedException {
        // ── Arrange ─────────────────────────────────────────────────────────
        String expectedDriverId = "drv_001";

        DriverCandidate candidate = DriverCandidate.builder()
                .driverId(expectedDriverId)
                .distanceKm(0.8)
                .rating(4.9)
                .declineRate(0.05)
                .tier("ECONOMY")
                .score(DriverCandidate.computeScore(0.8, 4.9, 0.05))
                .build();

        // Dispatch kill switch is OFF (false = not enabled)
        when(featureFlagService.isEnabled(anyString(), anyString(), anyBoolean()))
                .thenReturn(false);
        when(featureFlagService.isEnabled(anyString(), anyBoolean()))
                .thenReturn(false);

        // First call returns the candidate; subsequent calls (reassignment) return empty
        when(driverCandidateService.findCandidates(
                any(Double.class), any(Double.class), any(VehicleTier.class), any(Set.class), anyString()))
                .thenReturn(List.of(candidate))
                .thenReturn(List.of());

        // Mock Redisson distributed lock — always acquire immediately
        RLock mockLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        // ── Act ──────────────────────────────────────────────────────────────
        RideRequest request = new RideRequest();
        request.setRiderId("usr_integration_test");
        request.setPickupLat(12.9716);
        request.setPickupLng(77.5946);
        request.setDestinationLat(12.9352);
        request.setDestinationLng(77.6245);
        request.setTier(VehicleTier.ECONOMY);
        request.setPaymentMethod(PaymentMethod.CARD);
        request.setRegionId("ap-south-1");
        request.setTenantId("default");

        orchestrator.createRide(request, "idem-integration-001");

        // ── Assert ───────────────────────────────────────────────────────────
        // Wait up to 5 seconds for the Kafka message to arrive
        DriverOfferSentEvent offerEvent = offerMessages.poll(5, TimeUnit.SECONDS);

        assertThat(offerEvent)
                .as("driver.offer.sent event should be published to Kafka")
                .isNotNull();

        assertThat(offerEvent.getDriverId())
                .as("The matched driver should be %s", expectedDriverId)
                .isEqualTo(expectedDriverId);

        assertThat(offerEvent.getAttemptNumber())
                .as("First attempt should be attempt 1")
                .isEqualTo(1);

        assertThat(offerEvent.getTtlSeconds())
                .as("Offer TTL should be 15s")
                .isEqualTo(15);

        assertThat(offerEvent.getRideId())
                .as("rideId should be a non-empty UUID")
                .isNotBlank();
    }
}
