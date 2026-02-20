package com.ridehailing.surge.config;

import com.ridehailing.shared.featureflag.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FeatureFlagInitializer {

    private final FeatureFlagService featureFlagService;

    @Bean
    public ApplicationRunner seedSurgeFeatureFlags() {
        return args -> {
            featureFlagService.initDefaults("default");
            log.info("Feature flags initialised for surge-pricing-service");
        };
    }
}
