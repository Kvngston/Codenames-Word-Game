package com.tk.wordagents.config;

import com.tk.wordagents.config.RateLimiter.Limit;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(List<String> allowedOrigins, int roomIdleHours, StompRelay stompRelay, RateLimits rateLimits) {

    public record StompRelay(String host, int port, String login, String passcode, String virtualHost) {}

    /** Per client IP. See RateLimitInterceptor for which requests each one covers. */
    public record RateLimits(boolean enabled, Limit roomCreate, Limit roomJoin, Limit packCreate, Limit packChange, Limit packLookup) {}
}
