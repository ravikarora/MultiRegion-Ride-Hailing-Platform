package com.ridehailing.shared.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    public static final String TOPIC_INITIATED = "payment.initiated";
    public static final String TOPIC_CAPTURED  = "payment.captured";
    public static final String TOPIC_FAILED    = "payment.failed";

    private String paymentId;
    private String tripId;
    private String riderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String pspReference;
    private String status;
    private String failureReason;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant eventTime;
}
