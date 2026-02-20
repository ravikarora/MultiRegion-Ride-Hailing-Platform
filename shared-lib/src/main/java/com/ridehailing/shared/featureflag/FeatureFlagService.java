package com.ridehailing.shared.featureflag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * Feature flag service backed by Redis hashes.
 *
 * Key pattern:  feature-flags:{tenantId}
 * Field:        {flagName}
 * Value:        "true" | "false"
 *
 * Registered via FeatureFlagAutoConfiguration (Spring Boot auto-config).
 * Set a flag via Redis CLI:
 *   HSET feature-flags:default surge_pricing_enabled false
 *   HSET feature-flags:default dispatch_kill_switch true
 */
@Slf4j
@RequiredArgsConstructor
public class FeatureFlagService {

    private static final String FLAG_KEY_PREFIX = "feature-flags:";
    private static final String GLOBAL_TENANT   = "global";

    // Well-known flag names — keeps callers type-safe
    public static final String SURGE_PRICING_ENABLED  = "surge_pricing_enabled";
    public static final String AUTO_PAYMENT_CHARGE    = "auto_payment_charge";
    public static final String NEW_SCORING_ALGO       = "new_scoring_algo";
    public static final String DISPATCH_KILL_SWITCH   = "dispatch_kill_switch";
    public static final String REAL_TIME_TRACKING     = "real_time_tracking";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Returns true if the flag is enabled for the given tenant.
     * Falls back to global flag, then to the provided default value.
     */
    public boolean isEnabled(String tenantId, String flagName, boolean defaultValue) {
        // 1. Check per-tenant flag
        Object tenantVal = redisTemplate.opsForHash().get(FLAG_KEY_PREFIX + tenantId, flagName);
        if (tenantVal != null) {
            return Boolean.parseBoolean(tenantVal.toString());
        }

        // 2. Check global override
        Object globalVal = redisTemplate.opsForHash().get(FLAG_KEY_PREFIX + GLOBAL_TENANT, flagName);
        if (globalVal != null) {
            return Boolean.parseBoolean(globalVal.toString());
        }

        log.debug("Feature flag '{}' not found for tenant='{}', using default={}", flagName, tenantId, defaultValue);
        return defaultValue;
    }

    /**
     * Convenience: check flag for "default" tenant.
     */
    public boolean isEnabled(String flagName, boolean defaultValue) {
        return isEnabled("default", flagName, defaultValue);
    }

    /**
     * Set a flag programmatically (useful for tests or admin APIs).
     */
    public void setFlag(String tenantId, String flagName, boolean value) {
        redisTemplate.opsForHash().put(FLAG_KEY_PREFIX + tenantId, flagName, String.valueOf(value));
        log.info("Feature flag set: tenant={} flag={} value={}", tenantId, flagName, value);
    }

    /**
     * Initialise default flags if they are not yet set (called at startup).
     */
    public void initDefaults(String tenantId) {
        String key = FLAG_KEY_PREFIX + tenantId;
        setIfAbsent(key, SURGE_PRICING_ENABLED, "true");
        setIfAbsent(key, AUTO_PAYMENT_CHARGE,   "true");
        setIfAbsent(key, NEW_SCORING_ALGO,      "false");
        setIfAbsent(key, DISPATCH_KILL_SWITCH,  "false");
        setIfAbsent(key, REAL_TIME_TRACKING,    "true");
        redisTemplate.expire(key, Duration.ofDays(365)); // flags persist long-term
    }

    private void setIfAbsent(String key, String field, String value) {
        redisTemplate.opsForHash().putIfAbsent(key, field, value);
    }
}
