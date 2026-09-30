package com.tk.wordagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.tk.wordagents.config.RateLimiter.Limit;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private long now;
    private final RateLimiter limiter = new RateLimiter(() -> now);
    private final Limit limit = new Limit(3, Duration.ofMinutes(1));

    @Test
    void allowsTheBurstThenRefillsOneTokenAtATime() {
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("a", limit)).isZero();
        assertThat(limiter.tryAcquire("a", limit)).isEqualTo(Duration.ofMinutes(1));

        now += Duration.ofSeconds(45).toNanos();
        assertThat(limiter.tryAcquire("a", limit)).isEqualTo(Duration.ofSeconds(15));
        now += Duration.ofSeconds(15).toNanos();
        assertThat(limiter.tryAcquire("a", limit)).isZero();
        assertThat(limiter.tryAcquire("a", limit)).isPositive();
    }

    @Test
    void clientsHaveSeparateBucketsThatNeverOverfill() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("a", limit);
        assertThat(limiter.tryAcquire("b", limit)).isZero();

        now += Duration.ofHours(5).toNanos();
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("a", limit)).isZero();
        assertThat(limiter.tryAcquire("a", limit)).isPositive();
    }

    @Test
    void idleBucketsAreEvicted() {
        limiter.tryAcquire("a", limit);
        now += Duration.ofMinutes(2).toNanos();
        limiter.tryAcquire("b", limit);
        limiter.evictIdle(Duration.ofMinutes(1));
        assertThat(limiter.size()).isEqualTo(1);
    }
}
