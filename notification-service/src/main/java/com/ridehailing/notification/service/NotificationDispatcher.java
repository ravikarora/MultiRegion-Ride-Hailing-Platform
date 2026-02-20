package com.ridehailing.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub notification dispatcher.
 * In production: integrate with FCM (push), Twilio/SNS (SMS), SES (email).
 */
@Slf4j
@Service
public class NotificationDispatcher {

    public void sendPush(String userId, String title, String body) {
        log.info("[PUSH] userId={} title='{}' body='{}'", userId, title, body);
        // FCM/APNs integration would go here
    }

    public void sendSms(String phoneNumber, String message) {
        log.info("[SMS] to={} message='{}'", phoneNumber, message);
        // Twilio/AWS SNS integration would go here
    }

    public void sendEmail(String email, String subject, String body) {
        log.info("[EMAIL] to={} subject='{}'", email, subject);
        // AWS SES / SendGrid integration would go here
    }
}
