package com.ridehailing.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.notification.service.NotificationDispatcher;
import com.ridehailing.shared.events.DriverOfferSentEvent;
import com.ridehailing.shared.events.RideStatusChangedEvent;
import com.ridehailing.shared.events.TripEvent;
import com.ridehailing.shared.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideEventConsumer {

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    KafkaTopics.DRIVER_OFFER_SENT,
                    KafkaTopics.RIDE_ACCEPTED,
                    KafkaTopics.RIDE_DECLINED,
                    KafkaTopics.RIDE_CANCELLED,
                    KafkaTopics.RIDE_NO_DRIVER_FOUND,
                    KafkaTopics.RIDE_DRIVER_ARRIVED,
                    KafkaTopics.RIDE_IN_PROGRESS
            },
            groupId = "notification-service-rides",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRideEvents(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {

        try {
            switch (topic) {
                case KafkaTopics.DRIVER_OFFER_SENT -> {
                    DriverOfferSentEvent event = objectMapper.readValue(payload, DriverOfferSentEvent.class);
                    dispatcher.sendPush(event.getDriverId(),
                            "New ride request",
                            "You have a new ride request. Accept within " + event.getTtlSeconds() + "s");
                }
                case KafkaTopics.RIDE_ACCEPTED -> {
                    RideStatusChangedEvent event = objectMapper.readValue(payload, RideStatusChangedEvent.class);
                    dispatcher.sendPush(event.getRiderId(),
                            "Driver accepted",
                            "Your driver is on the way!");
                }
                case KafkaTopics.RIDE_DRIVER_ARRIVED -> {
                    RideStatusChangedEvent event = objectMapper.readValue(payload, RideStatusChangedEvent.class);
                    dispatcher.sendPush(event.getRiderId(),
                            "Driver arrived",
                            "Your driver has arrived at the pickup point!");
                    dispatcher.sendSms(event.getRiderId(),
                            "Your driver has arrived. Please come to the pickup point.");
                }
                case KafkaTopics.RIDE_IN_PROGRESS -> {
                    RideStatusChangedEvent event = objectMapper.readValue(payload, RideStatusChangedEvent.class);
                    dispatcher.sendPush(event.getRiderId(),
                            "Trip started",
                            "Your trip is now in progress. Have a safe ride!");
                }
                case KafkaTopics.RIDE_CANCELLED -> {
                    RideStatusChangedEvent event = objectMapper.readValue(payload, RideStatusChangedEvent.class);
                    dispatcher.sendPush(event.getRiderId(), "Ride cancelled", "Your ride has been cancelled.");
                    if (event.getDriverId() != null) {
                        dispatcher.sendPush(event.getDriverId(), "Ride cancelled", "The rider has cancelled.");
                    }
                }
                case KafkaTopics.RIDE_NO_DRIVER_FOUND -> {
                    RideStatusChangedEvent event = objectMapper.readValue(payload, RideStatusChangedEvent.class);
                    dispatcher.sendPush(event.getRiderId(),
                            "No drivers available",
                            "Sorry, no drivers found. Please try again.");
                }
                default -> log.debug("Unhandled topic: {}", topic);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process notification for topic {}: {}", topic, e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(
            topics = {KafkaTopics.TRIP_STARTED, KafkaTopics.TRIP_ENDED},
            groupId = "notification-service-trips",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTripEvents(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {

        try {
            TripEvent event = objectMapper.readValue(payload, TripEvent.class);
            switch (topic) {
                case KafkaTopics.TRIP_STARTED ->
                        dispatcher.sendPush(event.getRiderId(), "Trip started", "Your trip has started. Have a safe ride!");
                case KafkaTopics.TRIP_ENDED ->
                        dispatcher.sendPush(event.getRiderId(), "Trip completed",
                                "Your trip is complete. Total fare: " + event.getFareAmount() + " USD");
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process trip notification: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }
}
