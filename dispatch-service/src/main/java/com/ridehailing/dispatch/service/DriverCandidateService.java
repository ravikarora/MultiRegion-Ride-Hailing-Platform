package com.ridehailing.dispatch.service;

import com.ridehailing.dispatch.model.DriverCandidate;
import com.ridehailing.shared.enums.DriverStatus;
import com.ridehailing.shared.enums.VehicleTier;
import com.ridehailing.shared.featureflag.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds and scores nearby driver candidates using Redis GEORADIUS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverCandidateService {

    private static final String GEO_KEY_PREFIX = "drivers:geo:"; // region-scoped
    private static final String DRIVER_HASH_PREFIX = "driver:";
    private static final double SEARCH_RADIUS_KM = 5.0;

    // Scoring weights for the standard algorithm
    private static final double ALPHA = 0.5; // distance weight
    private static final double BETA  = 0.3; // rating weight
    private static final double GAMMA = 0.2; // decline-rate weight

    // Alternative weights for the new scoring algo (feature-flag gated)
    private static final double ALPHA_NEW = 0.4;
    private static final double BETA_NEW  = 0.4;
    private static final double GAMMA_NEW = 0.2;

    private final RedisTemplate<String, String> redisTemplate;
    private final FeatureFlagService featureFlagService;

    public List<DriverCandidate> findCandidates(
            double pickupLat, double pickupLng,
            VehicleTier requiredTier,
            Set<String> excludeDriverIds) {
        return findCandidates(pickupLat, pickupLng, requiredTier, excludeDriverIds, "default");
    }

    public List<DriverCandidate> findCandidates(
            double pickupLat, double pickupLng,
            VehicleTier requiredTier,
            Set<String> excludeDriverIds,
            String regionId) {

        String geoKey = GEO_KEY_PREFIX + (regionId != null ? regionId : "default");

        Circle circle = new Circle(
                new Point(pickupLng, pickupLat),
                new Distance(SEARCH_RADIUS_KM, Metrics.KILOMETERS)
        );

        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().radius(
                geoKey,
                circle,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(50)
        );

        if (geoResults == null) {
            return List.of();
        }

        List<DriverCandidate> candidates = new ArrayList<>();

        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : geoResults.getContent()) {
            String driverId = result.getContent().getName();
            if (excludeDriverIds.contains(driverId)) continue;

            Map<Object, Object> meta = redisTemplate.opsForHash().entries(DRIVER_HASH_PREFIX + driverId);
            if (meta.isEmpty()) continue;

            String status = (String) meta.get("status");
            String tier   = (String) meta.get("tier");
            double rating = parseDouble(meta.get("rating"), 4.0);

            // Only IDLE drivers of matching or higher tier
            if (!DriverStatus.IDLE.name().equals(status)) continue;
            if (!isTierCompatible(tier, requiredTier)) continue;

            double distKm = result.getDistance().getValue();
            double declineRate = parseDouble(meta.get("declineRate"), 0.1);

            // Feature-flag gated scoring algorithm
            boolean useNewAlgo = featureFlagService.isEnabled(
                    FeatureFlagService.NEW_SCORING_ALGO, false);
            double score = useNewAlgo
                    ? computeScore(distKm, rating, declineRate, ALPHA_NEW, BETA_NEW, GAMMA_NEW)
                    : computeScore(distKm, rating, declineRate, ALPHA, BETA, GAMMA);

            candidates.add(DriverCandidate.builder()
                    .driverId(driverId)
                    .distanceKm(distKm)
                    .rating(rating)
                    .declineRate(declineRate)
                    .tier(tier)
                    .score(score)
                    .build());
        }

        candidates.sort(Comparator.comparingDouble(DriverCandidate::getScore).reversed());
        log.debug("Found {} candidates near ({},{}) for tier {}", candidates.size(), pickupLat, pickupLng, requiredTier);
        return candidates;
    }

    public void markDriverStatus(String driverId, DriverStatus status) {
        String key = DRIVER_HASH_PREFIX + driverId;
        redisTemplate.opsForHash().put(key, "status", status.name());
    }

    double computeScore(double distKm, double rating, double declineRate,
                        double alpha, double beta, double gamma) {
        double distScore    = alpha * (1.0 / Math.max(distKm, 0.01));
        double ratingScore  = beta  * rating;
        double declineScore = gamma * (1.0 / Math.max(declineRate, 0.01));
        return distScore + ratingScore + declineScore;
    }

    private boolean isTierCompatible(String driverTier, VehicleTier requiredTier) {
        if (driverTier == null) return false;
        try {
            VehicleTier dt = VehicleTier.valueOf(driverTier);
            return dt.ordinal() >= requiredTier.ordinal();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private double parseDouble(Object val, double defaultVal) {
        if (val == null) return defaultVal;
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
