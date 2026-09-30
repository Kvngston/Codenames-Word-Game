package com.tk.wordagents.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token buckets kept in memory, one per client and rule. Production runs a
 * single app instance; with several, each would enforce its own share.
 */
public class RateLimiter {

    /** Up to {@code burst} requests at once, then one more every {@code every}. */
    public record Limit(int burst, Duration every) {}

    private static final class Bucket {
        double tokens;
        long updated;

        Bucket(double tokens, long updated) {
            this.tokens = tokens;
            this.updated = updated;
        }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public RateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    /** Takes a token and returns zero, or returns how long until the next one is free. */
    public Duration tryAcquire(String key, Limit limit) {
        long now = nanoTime.getAsLong();
        long every = limit.every().toNanos();
        Duration[] wait = {Duration.ZERO};
        buckets.compute(key, (k, bucket) -> {
            if (bucket == null) bucket = new Bucket(limit.burst(), now);
            bucket.tokens = Math.min(limit.burst(), bucket.tokens + (double) (now - bucket.updated) / every);
            bucket.updated = now;
            if (bucket.tokens >= 1) bucket.tokens -= 1;
            else wait[0] = Duration.ofNanos((long) Math.ceil((1 - bucket.tokens) * every));
            return bucket;
        });
        return wait[0];
    }

    /** Drops buckets that would be full again by now, so idle clients cost nothing. */
    public void evictIdle(Duration longestRefill) {
        long cutoff = nanoTime.getAsLong() - longestRefill.toNanos();
        buckets.values().removeIf(bucket -> bucket.updated < cutoff);
    }

    int size() {
        return buckets.size();
    }
}
