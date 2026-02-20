package com.ridehailing.surge.service;

import com.ridehailing.shared.events.SupplyDemandSnapshotEvent;
import com.ridehailing.shared.featureflag.FeatureFlagService;
import com.ridehailing.surge.entity.GeoCell;
import com.ridehailing.surge.metrics.SurgeMetrics;
import com.ridehailing.surge.repository.GeoCellRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Computes surge multiplier per H3 geo-cell (resolution 8, ≈0.74 km²).
 *
 * Algorithm — sliding window with recency weighting:
 *   1. Every snapshot is stored in a Redis sorted-set keyed by epoch-ms.
 *   2. On each compute, we retrieve all entries in the last WINDOW_SECONDS (5 min).
 *   3. Each entry is weighted by how recent it is: weight = (age_rank + 1) / totalEntries.
 *   4. Weighted demand ratio drives the surge formula.
 *   5. Multiplier is capped at MAX_SURGE (3.0×).
 *
 * This smooths out a single-snapshot spike while reacting to sustained demand.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeCalculatorService {

    private static final double SURGE_FACTOR  = 0.5;
    private static final double MAX_SURGE     = 3.0;
    private static final long   WINDOW_SECONDS = 300; // 5-minute sliding window
    private static final Duration CACHE_TTL   = Duration.ofSeconds(10); // snapshot interval

    // Redis key patterns
    private static final String SURGE_CACHE_PREFIX  = "surge:cell:";     // STRING  → current multiplier
    private static final String SURGE_WINDOW_PREFIX = "surge:window:";   // ZSET    → rolling history

    private final GeoCellRepository geoCellRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final FeatureFlagService featureFlagService;
    private final SurgeMetrics surgeMetrics;

    @Transactional
    public double processSnapshot(SupplyDemandSnapshotEvent event) {
        String cellId     = event.getGeoCell();
        long   nowMs      = Instant.now().toEpochMilli();
        long   cutoffMs   = nowMs - WINDOW_SECONDS * 1_000;
        String windowKey  = SURGE_WINDOW_PREFIX + cellId;

        // 1. Store snapshot in the sorted set: score=epochMs, member="drivers:rides"
        String member = event.getActiveDrivers() + ":" + event.getPendingRides();
        redisTemplate.opsForZSet().add(windowKey, member, nowMs);
        redisTemplate.expire(windowKey, Duration.ofSeconds(WINDOW_SECONDS + 60));

        // 2. Remove entries older than the window
        redisTemplate.opsForZSet().removeRangeByScore(windowKey, 0, cutoffMs);

        // 3. Compute recency-weighted multiplier from the window
        Set<TypedTuple<String>> windowEntries =
                redisTemplate.opsForZSet().rangeWithScores(windowKey, 0, -1);

        double multiplier = computeWindowedMultiplier(windowEntries, event.getActiveDrivers(), event.getPendingRides());

        // 4. Write current multiplier to fast-read cache (10s TTL matches snapshot cadence)
        String cacheKey = SURGE_CACHE_PREFIX + cellId;
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(multiplier), CACHE_TTL);

        // 5. Persist audit record to Postgres
        GeoCell cell = GeoCell.builder()
                .cellId(cellId)
                .regionId(event.getRegionId())
                .activeDrivers(event.getActiveDrivers())
                .pendingRides(event.getPendingRides())
                .surgeMultiplier(multiplier)
                .computedAt(Instant.now())
                .build();
        geoCellRepository.save(cell);

        surgeMetrics.updateMaxMultiplier(multiplier);

        log.info("Surge cell={} region={} drivers={} rides={} windowEntries={} multiplier={}",
                cellId, event.getRegionId(), event.getActiveDrivers(),
                event.getPendingRides(),
                windowEntries != null ? windowEntries.size() : 0,
                multiplier);

        return multiplier;
    }

    /**
     * Returns cached multiplier (10s TTL), then Postgres fallback, then 1.0 (no surge).
     * Returns 1.0 immediately if surge_pricing_enabled flag is off.
     */
    public double getSurgeMultiplier(String cellId) {
        // Feature flag kill switch — return 1.0 (no surge) when disabled
        if (!featureFlagService.isEnabled(FeatureFlagService.SURGE_PRICING_ENABLED, true)) {
            log.debug("Surge pricing disabled by feature flag for cell={}", cellId);
            surgeMetrics.recordFlagDisabled();
            return 1.0;
        }

        String cached = redisTemplate.opsForValue().get(SURGE_CACHE_PREFIX + cellId);
        if (cached != null) {
            return Double.parseDouble(cached);
        }
        return geoCellRepository.findById(cellId)
                .map(GeoCell::getSurgeMultiplier)
                .orElse(1.0);
    }

    /**
     * Recency-weighted demand ratio from sliding window.
     *
     * Entries in the ZSET are sorted ascending by epochMs (score).
     * We assign weight[i] = (i+1) / totalWeight so more-recent entries count more.
     * Falls back to current-snapshot-only if window is empty.
     */
    double computeWindowedMultiplier(Set<TypedTuple<String>> entries, int currentDrivers, int currentRides) {
        if (entries == null || entries.isEmpty()) {
            return clampSurge(computeInstantMultiplier(currentDrivers, currentRides));
        }

        int total = entries.size();
        int weightSum = total * (total + 1) / 2; // triangular number = sum 1..N

        double weightedDemandRatio = 0.0;
        int rank = 1; // oldest = 1, newest = total
        for (TypedTuple<String> entry : entries) {
            if (entry.getValue() == null) { rank++; continue; }
            String[] parts = entry.getValue().split(":");
            if (parts.length != 2) { rank++; continue; }
            int drivers = Integer.parseInt(parts[0]);
            int rides   = Integer.parseInt(parts[1]);
            double ratio = (double) rides / Math.max(drivers, 1);
            weightedDemandRatio += ratio * rank;
            rank++;
        }
        weightedDemandRatio /= weightSum;

        double surge = 1.0 + (weightedDemandRatio - 1.0) * SURGE_FACTOR;
        return clampSurge(surge);
    }

    double computeInstantMultiplier(int activeDrivers, int pendingRides) {
        double ratio = (double) pendingRides / Math.max(activeDrivers, 1);
        return 1.0 + (ratio - 1.0) * SURGE_FACTOR;
    }

    private double clampSurge(double surge) {
        return Math.min(Math.max(surge, 1.0), MAX_SURGE);
    }
}
