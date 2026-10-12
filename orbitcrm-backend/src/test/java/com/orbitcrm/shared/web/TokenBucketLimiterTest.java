package com.orbitcrm.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Unit test of {@link com.orbitcrm.shared.web.TokenBucketLimiter}.
 *
 * <p>Purpose: verify the rate-limiting algorithm with a controlled clock: a caller can burst up to the bucket
 * capacity, is then throttled with a retry delay, other callers have independent buckets, and tokens refill
 * continuously over time.
 *
 * @author Abhay
 */
class TokenBucketLimiterTest {

    @Test
    void allowsBurstUpToCapacityThenThrottlesAndRefills() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(60); // one token per second
        long t0 = 1_000_000_000L;

        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("user-1", t0)).isZero();
        }
        assertThat(limiter.tryAcquire("user-1", t0)).isEqualTo(1);
        // Other callers have their own bucket.
        assertThat(limiter.tryAcquire("user-2", t0)).isZero();
        // After a second, exactly one more request is allowed.
        long later = t0 + Duration.ofSeconds(1).toNanos();
        assertThat(limiter.tryAcquire("user-1", later)).isZero();
        assertThat(limiter.tryAcquire("user-1", later)).isPositive();
    }
}
