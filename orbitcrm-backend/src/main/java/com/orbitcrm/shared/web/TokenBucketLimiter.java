package com.orbitcrm.shared.web;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * In-memory token-bucket rate limiter keyed by caller (user id or client IP).
 *
 * <p>Purpose: throttle abusive API usage while allowing normal bursts. Each key has a bucket holding up to
 * {@code perMinute} tokens that refills continuously at {@code perMinute} tokens per minute; a request consumes one
 * token or is told how many seconds to wait. Buckets expire after ten minutes of inactivity. Used by the API
 * rate-limit filter. The state is per instance; a shared store (Redis) is needed when running several instances.
 *
 * @author Abhay
 */
public class TokenBucketLimiter {

    private final long capacity;
    private final double refillPerNano;
    private final Cache<String, Bucket> buckets;

    public TokenBucketLimiter(long perMinute) {
        this.capacity = perMinute;
        this.refillPerNano = perMinute / (double) Duration.ofMinutes(1).toNanos();
        this.buckets = Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(200_000).build();
    }

    /** Returns 0 if the request may proceed, otherwise the seconds to wait before retrying. */
    public long tryAcquire(String key) {
        return tryAcquire(key, System.nanoTime());
    }

    public long tryAcquire(String key, long nowNanos) {
        Bucket bucket = buckets.get(key, k -> new Bucket(capacity, nowNanos));
        synchronized (bucket) {
            double refilled = Math.min(capacity, bucket.tokens + (nowNanos - bucket.updatedAt) * refillPerNano);
            bucket.updatedAt = nowNanos;
            if (refilled >= 1) {
                bucket.tokens = refilled - 1;
                return 0;
            }
            bucket.tokens = refilled;
            double missing = 1 - refilled;
            return Math.max(1, (long) Math.ceil(missing / refillPerNano / 1_000_000_000d));
        }
    }

    /**
     * The mutable token bucket of one caller.
     *
     * <p>Purpose: hold the current number of tokens and the time of the last refill; access is synchronized per bucket.
     */
    private static final class Bucket {
        private double tokens;
        private long updatedAt;

        private Bucket(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }
    }
}
