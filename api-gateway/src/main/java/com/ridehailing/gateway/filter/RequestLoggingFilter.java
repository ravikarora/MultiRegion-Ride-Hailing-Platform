package com.ridehailing.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adds X-Request-ID trace header to all requests and logs method + path.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-Request-ID", requestId)
                .build();

        log.info("→ {} {} [requestId={}]",
                request.getMethod(), request.getPath(), requestId);

        return chain.filter(exchange.mutate().request(request).build())
                .then(Mono.fromRunnable(() ->
                        log.info("← {} {} [requestId={}] status={}",
                                request.getMethod(), request.getPath(), requestId,
                                exchange.getResponse().getStatusCode())));
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
