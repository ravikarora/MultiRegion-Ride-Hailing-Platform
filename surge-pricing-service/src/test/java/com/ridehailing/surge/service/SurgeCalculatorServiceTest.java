package com.ridehailing.surge.service;

import com.ridehailing.shared.featureflag.FeatureFlagService;
import com.ridehailing.surge.metrics.SurgeMetrics;
import com.ridehailing.surge.repository.GeoCellRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SurgeCalculatorService pure-math methods.
 *
 * Surge formula:
 *   instantMultiplier (raw) = 1.0 + (rides/max(drivers,1) - 1.0) * 0.5
 *   clampSurge applies [1.0, 3.0] bounds — only called via computeWindowedMultiplier.
 *
 * To test clamping behaviour, pass null/empty entries to computeWindowedMultiplier,
 * which delegates to clampSurge(computeInstantMultiplier(...)).
 */
@ExtendWith(MockitoExtension.class)
class SurgeCalculatorServiceTest {

    @Mock private GeoCellRepository geoCellRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private FeatureFlagService featureFlagService;
    @Mock private SurgeMetrics surgeMetrics;

    private SurgeCalculatorService service;

    @BeforeEach
    void setUp() {
        service = new SurgeCalculatorService(
                geoCellRepository, redisTemplate, featureFlagService, surgeMetrics);
    }

    // ─── computeInstantMultiplier — raw values (no clamping) ───────────────────

    @Test
    @DisplayName("Balanced supply-demand (10 rides, 10 drivers) → raw ratio=1 → 1.0")
    void instantMultiplier_balanced() {
        // ratio = 1.0 → raw = 1 + (1-1)*0.5 = 1.0
        double raw = service.computeInstantMultiplier(10, 10);
        assertThat(raw).isEqualTo(1.0);
    }

    @Test
    @DisplayName("2x demand (20 rides, 10 drivers) → raw = 1.5")
    void instantMultiplier_highDemand() {
        // ratio = 2.0 → raw = 1 + (2-1)*0.5 = 1.5
        double raw = service.computeInstantMultiplier(10, 20);
        assertThat(raw).isEqualTo(1.5);
    }

    @Test
    @DisplayName("No demand (0 rides, 10 drivers) → raw = 0.5 (unclamped)")
    void instantMultiplier_noDemand_rawBeforeClamp() {
        // ratio = 0.0 → raw = 1 + (0-1)*0.5 = 0.5  — clamping is done upstream
        double raw = service.computeInstantMultiplier(10, 0);
        assertThat(raw).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Supply > demand → raw < 1.0 (clamp applied by windowed path)")
    void instantMultiplier_excessSupply_rawBelowOne() {
        // ratio = 0.2 → raw = 1 + (0.2-1)*0.5 = 0.6
        double raw = service.computeInstantMultiplier(10, 2);
        assertThat(raw).isLessThan(1.0);
    }

    @Test
    @DisplayName("Zero drivers protected by max(drivers,1) floor — does not throw")
    void instantMultiplier_zeroDrivers_doesNotThrow() {
        assertThat(service.computeInstantMultiplier(0, 5)).isFinite();
    }

    // ─── Clamping tested via computeWindowedMultiplier(null/empty) ─────────────

    @Test
    @DisplayName("Null window → delegates to clampSurge(instant) → floor at 1.0")
    void windowedMultiplier_nullEntries_floorAtOne() {
        // 0 rides / 10 drivers → raw 0.5 → clamped to 1.0
        double result = service.computeWindowedMultiplier(null, 10, 0);
        assertThat(result).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Empty window → delegates to clampSurge(instant) → floor at 1.0")
    void windowedMultiplier_emptyEntries_floorAtOne() {
        double result = service.computeWindowedMultiplier(Set.of(), 10, 2);
        assertThat(result).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Extreme demand via null window → capped at MAX_SURGE = 3.0")
    void windowedMultiplier_extremeDemand_cappedAtMax() {
        // 100 rides / 1 driver → raw 50.5 → clamped to 3.0
        double result = service.computeWindowedMultiplier(null, 1, 100);
        assertThat(result).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Null window, balanced demand → no surge = 1.0")
    void windowedMultiplier_nullEntries_balanced() {
        double result = service.computeWindowedMultiplier(null, 10, 10);
        assertThat(result).isEqualTo(1.0);
    }

    // ─── computeWindowedMultiplier — window entries ─────────────────────────────

    @Test
    @DisplayName("Single window entry: windowed result equals instant (clamped) for that entry")
    void windowedMultiplier_singleEntry() {
        // One entry: "10:20" (10 drivers, 20 rides) → ratio=2 → 1.5
        Set<ZSetOperations.TypedTuple<String>> entries = new LinkedHashSet<>();
        entries.add(tuple("10:20"));

        double windowed = service.computeWindowedMultiplier(entries, 10, 20);
        assertThat(windowed).isEqualTo(1.5);
    }

    @Test
    @DisplayName("High sustained demand across window entries produces surge > 1.0")
    void windowedMultiplier_sustainedHighDemand() {
        Set<ZSetOperations.TypedTuple<String>> entries = new LinkedHashSet<>();
        entries.add(tuple("5:10"));   // older  — 2x demand
        entries.add(tuple("5:10"));   // newer  — 2x demand

        double result = service.computeWindowedMultiplier(entries, 5, 10);
        assertThat(result).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("Windowed result is always within [1.0, 3.0] regardless of inputs")
    void windowedMultiplier_alwaysClamped() {
        Set<ZSetOperations.TypedTuple<String>> entries = new LinkedHashSet<>();
        entries.add(tuple("1:1000"));
        entries.add(tuple("1:1000"));
        entries.add(tuple("1:1000"));

        double result = service.computeWindowedMultiplier(entries, 1, 1000);
        assertThat(result).isBetween(1.0, 3.0);
    }

    @Test
    @DisplayName("More recent snapshot is weighted more heavily than older ones")
    void windowedMultiplier_recencyWeighted() {
        // Scenario A: high demand in latest entry, balanced in oldest
        Set<ZSetOperations.TypedTuple<String>> recentHighDemand = new LinkedHashSet<>();
        recentHighDemand.add(tuple("10:10")); // older  — balanced (ratio=1)
        recentHighDemand.add(tuple("2:20"));  // recent — high demand (ratio=10)

        // Scenario B: balanced in latest, high demand in oldest
        Set<ZSetOperations.TypedTuple<String>> recentLowDemand = new LinkedHashSet<>();
        recentLowDemand.add(tuple("2:20"));   // older  — high demand (ratio=10)
        recentLowDemand.add(tuple("10:10"));  // recent — balanced (ratio=1)

        double surgeHighRecent = service.computeWindowedMultiplier(recentHighDemand, 2, 20);
        double surgeLowRecent  = service.computeWindowedMultiplier(recentLowDemand,  10, 10);

        // The scenario with high demand in the most-recent entry should produce more surge
        assertThat(surgeHighRecent).isGreaterThan(surgeLowRecent);
    }

    // ─── helper — only stub getValue(); getScore() is never called by the impl ──

    private ZSetOperations.TypedTuple<String> tuple(String value) {
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> t = mock(ZSetOperations.TypedTuple.class);
        when(t.getValue()).thenReturn(value);
        return t;
    }
}
