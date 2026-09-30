package com.tk.wordagents.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(List<String> allowedOrigins, int roomIdleHours, StompRelay stompRelay) {

    public record StompRelay(String host, int port, String login, String passcode, String virtualHost) {}
}
