package com.tk.wordagents.config;

import com.tk.wordagents.config.RateLimiter.Limit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Limits the API calls that create things (rooms, seats, packs) per client IP.
 * Moves inside a game aren't limited. Runs as an MVC interceptor, after CORS,
 * so a refused request still reaches the browser with its error message.
 */
@Component
class RateLimitInterceptor implements HandlerInterceptor {

    private record Rule(String name, String what, HttpMethod method, PathPattern path, Limit limit) {}

    private final RateLimiter limiter = new RateLimiter(System::nanoTime);
    private final boolean enabled;
    private final List<Rule> rules;
    private final Duration longestRefill;

    RateLimitInterceptor(AppProperties properties) {
        AppProperties.RateLimits limits = properties.rateLimits();
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.enabled = limits.enabled();
        this.rules = List.of(
            new Rule("room-create", "new rooms", HttpMethod.POST, parser.parse("/api/rooms"), limits.roomCreate()),
            new Rule("room-join", "room joins", HttpMethod.POST, parser.parse("/api/rooms/{code}/players"), limits.roomJoin()),
            new Rule("pack-create", "new word packs", HttpMethod.POST, parser.parse("/api/packs"), limits.packCreate()),
            new Rule("pack-change", "word pack changes", HttpMethod.PUT, parser.parse("/api/packs/{code}"), limits.packChange()),
            new Rule("pack-change", "word pack changes", HttpMethod.DELETE, parser.parse("/api/packs/{code}"), limits.packChange()),
            new Rule("pack-lookup", "word pack lookups", HttpMethod.GET, parser.parse("/api/packs/{code}"), limits.packLookup()));
        this.longestRefill = rules.stream()
            .map(rule -> rule.limit().every().multipliedBy(rule.limit().burst()))
            .max(Comparator.naturalOrder())
            .orElse(Duration.ZERO);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) return true;
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        for (Rule rule : rules) {
            if (!rule.method().matches(request.getMethod()) || !rule.path().matches(path)) continue;
            // Behind Caddy this is the client's address (server.forward-headers-strategy: native).
            Duration wait = limiter.tryAcquire(rule.name() + "|" + request.getRemoteAddr(), rule.limit());
            if (!wait.isZero()) throw new RateLimitException(rule.what(), wait);
        }
        return true;
    }

    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictIdle() {
        limiter.evictIdle(longestRefill);
    }
}
