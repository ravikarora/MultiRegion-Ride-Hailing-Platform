package com.ridehailing.dispatch.config;

import com.ridehailing.shared.featureflag.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds default feature flags for the "default" tenant on startup.
 * Flags already set in Redis are NOT overwritten (putIfAbsent).
 *
 * To toggle a flag at runtime without restart:
 *   redis-cli HSET feature-flags:default dispatch_kill_switch true
 *   redis-cli HSET feature-flags:default surge_pricing_enabled false
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FeatureFlagInitializer {

    private final FeatureFlagService featureFlagService;

    @Bean
    public ApplicationRunner seedFeatureFlags() {
        return args -> {
            featureFlagService.initDefaults("default");
            log.info("Feature flags initialised for tenant=default");
        };
    }
}
