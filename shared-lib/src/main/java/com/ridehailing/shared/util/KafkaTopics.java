package com.ridehailing.shared.util;

/**
 * Central registry of all Kafka topic names.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String DRIVER_LOCATION_UPDATED  = "driver.location.updated";
    public static final String RIDE_REQUESTED           = "ride.requested";
    public static final String DRIVER_OFFER_SENT        = "driver.offer.sent";
    public static final String DRIVER_OFFER_EXPIRED     = "driver.offer.expired";
    public static final String RIDE_ACCEPTED            = "ride.accepted";
    public static final String RIDE_DECLINED            = "ride.declined";
    public static final String RIDE_CANCELLED           = "ride.cancelled";
    public static final String RIDE_NO_DRIVER_FOUND     = "ride.no_driver_found";
    public static final String RIDE_DRIVER_ARRIVED      = "ride.driver_arrived";
    public static final String RIDE_IN_PROGRESS         = "ride.in_progress";
    public static final String TRIP_STARTED             = "trip.started";
    public static final String TRIP_PAUSED              = "trip.paused";
    public static final String TRIP_ENDED               = "trip.ended";
    public static final String PAYMENT_INITIATED        = "payment.initiated";
    public static final String PAYMENT_CAPTURED         = "payment.captured";
    public static final String PAYMENT_FAILED           = "payment.failed";
    public static final String SUPPLY_DEMAND_SNAPSHOT   = "supply.demand.snapshot";
}
