package com.tk.wordagents.config;

import com.tk.wordagents.room.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at /ws, relayed through RabbitMQ so several server
 * instances can push to players connected to any of them.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final StompAuthInterceptor auth;

    // Lazy: the interceptor's dependencies need the user registry this configuration creates.
    WebSocketConfig(AppProperties properties, @Lazy StompAuthInterceptor auth) {
        this.properties = properties;
        this.auth = auth;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        AppProperties.StompRelay relay = properties.stompRelay();
        // Per-player views use /topic user destinations: RabbitMQ gives each
        // subscription an auto-deleted queue, so closed sessions leave nothing behind.
        registry.enableStompBrokerRelay("/topic")
            .setRelayHost(relay.host())
            .setRelayPort(relay.port())
            .setClientLogin(relay.login())
            .setClientPasscode(relay.passcode())
            .setSystemLogin(relay.login())
            .setSystemPasscode(relay.passcode())
            .setVirtualHost(relay.virtualHost())
            // Share user sessions across instances: unresolved user messages and the
            // user registry are broadcast so any instance can reach any player.
            .setUserDestinationBroadcast("/topic/unresolved-user-destination")
            .setUserRegistryBroadcast("/topic/simp-user-registry");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
