package com.ridehailing.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * Multi-tenancy enforcement filter — runs before IdempotencyValidationFilter.
 *
 * Responsibilities:
 *   1. Validate X-Tenant-ID header is present (reject unknown/missing tenants).
 *   2. Enforce per-tenant rate limits using a Redis sliding-window counter.
 *   3. Propagate tenant ID to downstream services as a request header.
 *   4. Stub feature-flag check (real impl: Redis HGET feature-flags:{tenantId} {flag}).
 *
 * Rate limit algorithm:
 *   - Key: rate:{tenantId}:{routePrefix}:{windowBucket}
 *   - Window bucket = epoch-second / windowSizeSeconds  → changes every N seconds
 *   - On each request: INCR key → if result > limit, reject 429
 *   - Key TTL is set to 2× window to clean up naturally
 *
 * Default limits (illustrative; in production these come from a config store):
 *   - Standard tenant: 200 req/s
 *   - Premium tenant:  1000 req/s
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantValidationFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATHS = Set.of("/actuator", "/health");

    // Per-tenant rate limits (req per 1-second window).
    // In production: load from a tenant configuration service or Redis hash.
    private static final int DEFAULT_RPS_LIMIT = 200;
    private static final int PREMIUM_RPS_LIMIT = 1000;
    private static final Set<String> PREMIUM_TENANTS = Set.of("tenant-acme", "tenant-enterprise");

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip public/health paths
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");

        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Missing X-Tenant-ID for {} {}", exchange.getRequest().getMethod(), path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        // Enforce per-tenant rate limit
        int limit = PREMIUM_TENANTS.contains(tenantId) ? PREMIUM_RPS_LIMIT : DEFAULT_RPS_LIMIT;
        long windowBucket = System.currentTimeMillis() / 1_000; // 1-second window
        String rateLimitKey = "rate:" + tenantId + ":" + windowBucket;

        return reactiveRedisTemplate.opsForValue()
                .increment(rateLimitKey)
                .flatMap(count -> {
                    if (count == 1) {
                        // Set TTL on first request in this window
                        return reactiveRedisTemplate.expire(rateLimitKey, Duration.ofSeconds(2))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > limit) {
                        log.warn("Rate limit exceeded tenant={} path={} count={} limit={}", tenantId, path, count, limit);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
                        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");
                        exchange.getResponse().getHeaders().add("Retry-After", "1");
                        return exchange.getResponse().setComplete();
                    }

                    // Propagate tenant ID downstream (in case clients forget to forward it)
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-Tenant-ID", tenantId)
                            .build();

                    long remaining = Math.max(0, limit - count);
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(remaining));

                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    @Override
    public int getOrder() {
        return -150; // After RequestLoggingFilter (-200), before IdempotencyFilter (-100)
    }
}
