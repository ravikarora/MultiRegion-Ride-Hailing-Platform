package com.ridehailing.shared.featureflag;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Auto-configuration that registers FeatureFlagService whenever
 * spring-data-redis is on the classpath. Services that include
 * shared-lib will automatically get this bean — no component-scan
 * changes needed in each service.
 */
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
public class FeatureFlagAutoConfiguration {

    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    public FeatureFlagService featureFlagService(RedisTemplate<String, String> redisTemplate) {
        return new FeatureFlagService(redisTemplate);
    }
}
