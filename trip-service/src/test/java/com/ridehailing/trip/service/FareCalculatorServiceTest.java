package com.ridehailing.trip.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FareCalculatorService.
 *
 * Formula: fare = (BASE_FARE + distKm * PER_KM + durationMin * PER_MIN) * surge
 *                = (2.00 + dist * 1.50 + durationSec/60 * 0.25) * surge
 */
class FareCalculatorServiceTest {

    private FareCalculatorService calculator;

    @BeforeEach
    void setUp() {
        calculator = new FareCalculatorService();
    }

    @Test
    @DisplayName("baseFare(10 km, 10 min) = 2 + 15 + 2.50 = 19.50")
    void baseFare_standardTrip() {
        // 2.00 + (10 * 1.50) + (600s / 60 * 0.25) = 2 + 15 + 2.50 = 19.50
        BigDecimal fare = calculator.baseFare(10.0, 600L);
        assertThat(fare).isEqualByComparingTo("19.50");
    }

    @Test
    @DisplayName("baseFare(0 km, 0 s) = BASE_FARE only = 2.00")
    void baseFare_zeroDurationAndDistance() {
        BigDecimal fare = calculator.baseFare(0.0, 0L);
        assertThat(fare).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("calculate with 2x surge doubles the base fare")
    void calculate_surgeDoublesFare() {
        BigDecimal noSurge  = calculator.calculate(10.0, 600L, 1.0);
        BigDecimal withSurge = calculator.calculate(10.0, 600L, 2.0);
        // withSurge should be exactly double
        assertThat(withSurge).isEqualByComparingTo(noSurge.multiply(BigDecimal.valueOf(2)));
    }

    @Test
    @DisplayName("calculate with 3x surge caps correctly (not tested here, just arithmetic)")
    void calculate_3xSurge() {
        // (2 + 5*1.5 + 5*0.25) * 3 = (2 + 7.5 + 1.25) * 3 = 10.75 * 3 = 32.25
        // durationSeconds = 300 → 5 min
        BigDecimal fare = calculator.calculate(5.0, 300L, 3.0);
        assertThat(fare).isEqualByComparingTo("32.25");
    }

    @Test
    @DisplayName("Result is always rounded to 2 decimal places")
    void calculate_alwaysTwoDecimalPlaces() {
        BigDecimal fare = calculator.calculate(1.1, 67L, 1.0);
        // scale must be exactly 2
        assertThat(fare.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Longer trips cost more than shorter trips (same surge)")
    void calculate_longerTripCostsMore() {
        BigDecimal shortTrip = calculator.calculate(2.0, 120L, 1.0);
        BigDecimal longTrip  = calculator.calculate(20.0, 1200L, 1.0);
        assertThat(longTrip).isGreaterThan(shortTrip);
    }

    @Test
    @DisplayName("baseFare is equivalent to calculate with surge=1.0")
    void baseFare_equivalentToSurgeOne() {
        BigDecimal viaBase    = calculator.baseFare(7.5, 480L);
        BigDecimal viaCalc    = calculator.calculate(7.5, 480L, 1.0);
        assertThat(viaBase).isEqualByComparingTo(viaCalc);
    }
}
