package com.tk.wordagents.config;

import java.time.Duration;

/** A client went over one of the {@link RateLimitInterceptor} limits; reported as 429. */
public class RateLimitException extends RuntimeException {

    private final Duration retryAfter;

    RateLimitException(String what, Duration retryAfter) {
        super("Too many " + what + " from your network. Try again in " + describe(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return Math.max(1, (long) Math.ceil(retryAfter.toMillis() / 1000.0));
    }

    private static String describe(Duration wait) {
        long seconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
        if (seconds < 60) return seconds == 1 ? "a second" : seconds + " seconds";
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "a minute" : minutes + " minutes";
    }
}
