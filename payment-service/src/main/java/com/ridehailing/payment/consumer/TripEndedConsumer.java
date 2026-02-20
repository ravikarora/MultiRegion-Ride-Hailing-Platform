package com.ridehailing.payment.consumer;

import com.ridehailing.payment.service.PaymentOrchestrator;
import com.ridehailing.shared.events.TripEvent;
import com.ridehailing.shared.enums.TripStatus;
import com.ridehailing.shared.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEndedConsumer {

    private final PaymentOrchestrator paymentOrchestrator;

    @KafkaListener(
            topics = KafkaTopics.TRIP_ENDED,
            groupId = "payment-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(TripEvent event, Acknowledgment ack) {
        if (event.getStatus() != TripStatus.ENDED) {
            ack.acknowledge();
            return;
        }
        if (event.getFareAmount() == null) {
            log.warn("Trip {} ended with no fare amount, skipping payment", event.getTripId());
            ack.acknowledge();
            return;
        }
        try {
            paymentOrchestrator.initiatePayment(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Unrecoverable error processing payment for trip {}: {}", event.getTripId(), e.getMessage(), e);
            ack.acknowledge(); // DLQ handling would go here in production
        }
    }
}
