package com.ridehailing.shared.context;

/**
 * Holds the current tenant identifier for the duration of a request thread.
 *
 * Set by the API Gateway filter (X-Tenant-ID header) and propagated via MDC
 * and Kafka message headers. Virtual-thread-safe: each virtual thread gets
 * its own ThreadLocal slot because virtual threads are not pooled the same way.
 */
public final class TenantContext {

    public static final String HEADER_TENANT_ID   = "X-Tenant-ID";
    public static final String KAFKA_HEADER_TENANT = "tenant_id";
    public static final String DEFAULT_TENANT      = "default";

    private static final ThreadLocal<String> TENANT = ThreadLocal.withInitial(() -> DEFAULT_TENANT);

    private TenantContext() {}

    public static void set(String tenantId) {
        TENANT.set(tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId);
    }

    public static String get() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
