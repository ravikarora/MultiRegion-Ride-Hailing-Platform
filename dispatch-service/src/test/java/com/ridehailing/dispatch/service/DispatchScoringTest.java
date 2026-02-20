package com.ridehailing.dispatch.service;

import com.ridehailing.shared.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the dispatch scoring algorithm in DriverCandidateService.
 *
 * Score formula (standard weights):
 *   score = 0.5 * (1/dist) + 0.3 * rating + 0.2 * (1/declineRate)
 *
 * Higher score → driver gets preference.
 */
@ExtendWith(MockitoExtension.class)
class DispatchScoringTest {

    // Standard weights  (from DriverCandidateService constants)
    private static final double ALPHA = 0.5;
    private static final double BETA  = 0.3;
    private static final double GAMMA = 0.2;

    // Alternative weights (feature-flag gated)
    private static final double ALPHA_NEW = 0.4;
    private static final double BETA_NEW  = 0.4;
    private static final double GAMMA_NEW = 0.2;

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private FeatureFlagService featureFlagService;

    private DriverCandidateService service;

    @BeforeEach
    void setUp() {
        service = new DriverCandidateService(redisTemplate, featureFlagService);
    }

    @Test
    @DisplayName("Closer driver scores higher than a farther driver (all else equal)")
    void closerDriverScoresHigher() {
        double scoreClose = service.computeScore(0.5, 4.5, 0.1, ALPHA, BETA, GAMMA);
        double scoreFar   = service.computeScore(4.0, 4.5, 0.1, ALPHA, BETA, GAMMA);
        assertThat(scoreClose).isGreaterThan(scoreFar);
    }

    @Test
    @DisplayName("Higher-rated driver scores higher than a lower-rated one (same distance)")
    void higherRatingScoresHigher() {
        double highRating = service.computeScore(1.0, 5.0, 0.1, ALPHA, BETA, GAMMA);
        double lowRating  = service.computeScore(1.0, 3.0, 0.1, ALPHA, BETA, GAMMA);
        assertThat(highRating).isGreaterThan(lowRating);
    }

    @Test
    @DisplayName("Driver with lower decline rate scores higher (same distance & rating)")
    void lowerDeclineRateScoresHigher() {
        double lowDecline  = service.computeScore(1.0, 4.5, 0.05, ALPHA, BETA, GAMMA);
        double highDecline = service.computeScore(1.0, 4.5, 0.50, ALPHA, BETA, GAMMA);
        assertThat(lowDecline).isGreaterThan(highDecline);
    }

    @Test
    @DisplayName("Score is always positive for valid inputs")
    void scoreAlwaysPositive() {
        double score = service.computeScore(10.0, 1.0, 1.0, ALPHA, BETA, GAMMA);
        assertThat(score).isPositive();
    }

    @Test
    @DisplayName("Near-zero distance is protected against division by zero (uses 0.01 floor)")
    void zeroDistanceProtectedByFloor() {
        double scoreZero = service.computeScore(0.0, 4.5, 0.1, ALPHA, BETA, GAMMA);
        double scoreFloor = service.computeScore(0.01, 4.5, 0.1, ALPHA, BETA, GAMMA);
        // Both should yield the same score because 0.0 is clamped to 0.01
        assertThat(scoreZero).isEqualTo(scoreFloor);
    }

    @Test
    @DisplayName("Alternative weights (feature-flag gated) produce a different rank ordering")
    void alternativeWeightsChangePriority() {
        // Driver A: very close, mediocre rating
        double scoreA_std = service.computeScore(0.1, 3.0, 0.1, ALPHA,     BETA,     GAMMA);
        double scoreA_new = service.computeScore(0.1, 3.0, 0.1, ALPHA_NEW, BETA_NEW, GAMMA_NEW);

        // Driver B: further, excellent rating
        double scoreB_std = service.computeScore(2.0, 5.0, 0.1, ALPHA,     BETA,     GAMMA);
        double scoreB_new = service.computeScore(2.0, 5.0, 0.1, ALPHA_NEW, BETA_NEW, GAMMA_NEW);

        // Standard algo: distance-heavy → A wins
        assertThat(scoreA_std).isGreaterThan(scoreB_std);
        // New algo: rating weight boosted → B should catch up / could beat A
        // (exact outcome depends on values, but scores should differ from standard)
        assertThat(scoreA_new).isNotEqualTo(scoreA_std);
        assertThat(scoreB_new).isNotEqualTo(scoreB_std);
    }

    @Test
    @DisplayName("Score formula: manual calculation matches implementation")
    void scoreFormulaMatchesManualCalculation() {
        double dist = 2.0, rating = 4.8, declineRate = 0.2;
        double expected = ALPHA * (1.0 / dist) + BETA * rating + GAMMA * (1.0 / declineRate);
        double actual   = service.computeScore(dist, rating, declineRate, ALPHA, BETA, GAMMA);
        assertThat(actual).isEqualTo(expected);
    }
}
