package com.ridehailing.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for generating idempotency hash from response payloads.
 * Stored alongside the idempotency_key in Postgres to detect replays.
 */
public final class IdempotencyUtil {

    private IdempotencyUtil() {}

    public static String hashPayload(String json) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Redis key for idempotency cache (TTL 24h is set at call site).
     */
    public static String redisKey(String service, String idempotencyKey) {
        return "idempotency:" + service + ":" + idempotencyKey;
    }
}
