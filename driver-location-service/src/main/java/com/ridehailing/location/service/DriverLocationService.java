package com.ridehailing.location.service;

import com.ridehailing.location.model.LocationUpdateRequest;
import com.ridehailing.shared.events.DriverLocationUpdatedEvent;
import com.ridehailing.shared.events.SupplyDemandSnapshotEvent;
import com.ridehailing.shared.util.H3Util;
import com.ridehailing.shared.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverLocationService {

    private static final String GEO_KEY_PREFIX = "drivers:geo:"; // region-scoped: drivers:geo:{regionId}
    private static final String DRIVER_HASH_PREFIX = "driver:";
    private static final long DRIVER_TTL_SECONDS = 30;

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Key format: "{regionId}:{h3Cell}" — encodes both dimensions so the snapshot
     * publisher can recover the regionId without a secondary lookup.
     */
    private final ConcurrentHashMap<String, AtomicInteger> cellDriverCounts = new ConcurrentHashMap<>();

    /** Region-scoped geo index key. Drivers in different regions never pollute each other. */
    private static String geoKey(String regionId) {
        return GEO_KEY_PREFIX + (regionId != null ? regionId : "default");
    }

    public void updateLocation(LocationUpdateRequest req) {
        String driverId = req.getDriverId();
        String regionId = req.getRegionId() != null ? req.getRegionId() : "default";

        // Store geo position in the region-scoped index
        redisTemplate.opsForGeo().add(
                geoKey(regionId),
                new Point(req.getLongitude(), req.getLatitude()),
                driverId
        );

        // Store driver metadata hash
        String hashKey = DRIVER_HASH_PREFIX + driverId;
        Map<String, String> driverMeta = new HashMap<>();
        driverMeta.put("status", req.getStatus().name());
        driverMeta.put("tier", req.getTier().name());
        driverMeta.put("rating", String.valueOf(req.getRating()));
        driverMeta.put("lastSeen", Instant.now().toString());
        driverMeta.put("regionId", regionId);
        driverMeta.put("lat", String.valueOf(req.getLatitude()));
        driverMeta.put("lng", String.valueOf(req.getLongitude()));
        redisTemplate.opsForHash().putAll(hashKey, driverMeta);
        redisTemplate.expire(hashKey, java.time.Duration.ofSeconds(DRIVER_TTL_SECONDS));

        // Track per-cell driver counts keyed as "regionId:h3Cell" so the snapshot
        // publisher can reconstruct both dimensions without an extra lookup.
        String cell = H3Util.surgeCell(req.getLatitude(), req.getLongitude());
        String cellKey = regionId + ":" + cell;
        cellDriverCounts.computeIfAbsent(cellKey, k -> new AtomicInteger(0)).incrementAndGet();

        // Publish location event to Kafka
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverId(driverId)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .regionId(req.getRegionId())
                .status(req.getStatus().name())
                .tier(req.getTier().name())
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(KafkaTopics.DRIVER_LOCATION_UPDATED, driverId, event);
        log.debug("Location updated for driver {} at ({},{})", driverId, req.getLatitude(), req.getLongitude());
    }

    /**
     * Every 10 seconds publish supply/demand snapshots per geo cell.
     * Downstream SurgePricingService consumes this to compute multipliers.
     */
    @Scheduled(fixedDelayString = "${location.snapshot.interval-ms:10000}")
    public void publishSupplyDemandSnapshot() {
        cellDriverCounts.forEach((cellKey, counter) -> {
            int count = counter.getAndSet(0);
            if (count == 0) return;

            // Key format: "{regionId}:{h3Cell}" — split at first ':' only
            int sep = cellKey.indexOf(':');
            String regionId = sep > 0 ? cellKey.substring(0, sep) : "default";
            String h3Cell   = sep > 0 ? cellKey.substring(sep + 1) : cellKey;

            SupplyDemandSnapshotEvent snapshot = SupplyDemandSnapshotEvent.builder()
                    .geoCell(h3Cell)
                    .regionId(regionId)
                    .activeDrivers(count)
                    .pendingRides(0)
                    .computedAt(Instant.now())
                    .build();

            kafkaTemplate.send(KafkaTopics.SUPPLY_DEMAND_SNAPSHOT, h3Cell, snapshot);
            log.debug("Snapshot: region={} cell={} drivers={}", regionId, h3Cell, count);
        });
    }

    public GeoResults<RedisGeoCommands.GeoLocation<String>> getNearbyDrivers(
            double lat, double lng, double radiusKm, String regionId) {
        Circle circle = new Circle(
                new Point(lng, lat),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );
        return redisTemplate.opsForGeo().radius(
                geoKey(regionId),
                circle,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
                        .limit(20)
        );
    }

    public Map<Object, Object> getDriverMeta(String driverId) {
        return redisTemplate.opsForHash().entries(DRIVER_HASH_PREFIX + driverId);
    }
}
