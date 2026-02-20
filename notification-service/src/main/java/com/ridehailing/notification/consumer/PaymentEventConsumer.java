package com.ridehailing.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.notification.service.NotificationDispatcher;
import com.ridehailing.shared.events.PaymentEvent;
import com.ridehailing.shared.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

/**
 * Listens to payment events and sends rider receipts.
 *
 * payment.captured → push + SMS receipt with fare breakdown
 * payment.failed   → push alert asking rider to update payment method
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {KafkaTopics.PAYMENT_CAPTURED, KafkaTopics.PAYMENT_FAILED},
            groupId = "notification-service-payments",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentEvents(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {

        try {
            PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);

            switch (topic) {
                case KafkaTopics.PAYMENT_CAPTURED -> sendReceipt(event);
                case KafkaTopics.PAYMENT_FAILED   -> sendPaymentFailureAlert(event);
                default -> log.debug("Unhandled payment topic: {}", topic);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment notification for topic {}: {}", topic, e.getMessage(), e);
            ack.acknowledge(); // do not block partition — ops can replay from outbox
        }
    }

    private void sendReceipt(PaymentEvent event) {
        String amountStr = event.getAmount() != null
                ? event.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "N/A";
        String currency  = event.getCurrency() != null ? event.getCurrency() : "USD";

        String pushBody = String.format(
                "Payment of %s %s received. PSP ref: %s. Thank you for riding!",
                amountStr, currency, event.getPspReference());

        String smsBody = String.format(
                "Ride receipt: %s %s charged to your card. Ref: %s",
                amountStr, currency, event.getPspReference());

        dispatcher.sendPush(event.getRiderId(), "Payment confirmed ✔", pushBody);
        dispatcher.sendSms(event.getRiderId(), smsBody);

        log.info("Receipt sent to rider={} amount={} {} ref={}",
                event.getRiderId(), amountStr, currency, event.getPspReference());
    }

    private void sendPaymentFailureAlert(PaymentEvent event) {
        dispatcher.sendPush(
                event.getRiderId(),
                "Payment failed",
                "We couldn't process your payment. Please update your payment method in the app.");

        log.warn("Payment failure notification sent to rider={} paymentId={}",
                event.getRiderId(), event.getPaymentId());
    }
}
