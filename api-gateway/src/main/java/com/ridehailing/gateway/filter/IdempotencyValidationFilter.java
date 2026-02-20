package com.ridehailing.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Global filter that enforces Idempotency-Key header on all state-mutating POST requests.
 */
@Slf4j
@Component
public class IdempotencyValidationFilter implements GlobalFilter, Ordered {

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/v1/locations",  // location updates are high-frequency, exempt
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        boolean exempt = EXEMPT_PATHS.stream().anyMatch(path::startsWith);
        if (exempt) {
            return chain.filter(exchange);
        }

        String idempotencyKey = exchange.getRequest().getHeaders().getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Missing Idempotency-Key for POST {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
