package com.ridehailing.trip.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fare calculation: base fare + per-km + per-minute + surge multiplier.
 *
 * Formula:
 *   fare = (BASE_FARE + distanceKm * PER_KM_RATE + durationMin * PER_MIN_RATE) * surgeMultiplier
 */
@Slf4j
@Service
public class FareCalculatorService {

    private static final BigDecimal BASE_FARE       = new BigDecimal("2.00");
    private static final BigDecimal PER_KM_RATE     = new BigDecimal("1.50");
    private static final BigDecimal PER_MINUTE_RATE = new BigDecimal("0.25");

    public BigDecimal calculate(double distanceKm, long durationSeconds, double surgeMultiplier) {
        BigDecimal distance = BigDecimal.valueOf(distanceKm);
        BigDecimal durationMin = BigDecimal.valueOf(durationSeconds).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal surge = BigDecimal.valueOf(surgeMultiplier);

        BigDecimal fare = BASE_FARE
                .add(distance.multiply(PER_KM_RATE))
                .add(durationMin.multiply(PER_MINUTE_RATE))
                .multiply(surge)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Fare calc: dist={}km duration={}s surge={} -> {}", distanceKm, durationSeconds, surgeMultiplier, fare);
        return fare;
    }

    public BigDecimal baseFare(double distanceKm, long durationSeconds) {
        return calculate(distanceKm, durationSeconds, 1.0);
    }
}
