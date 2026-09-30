package com.tk.wordagents.room;

import java.util.Locale;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Guards the WebSocket. CONNECT must carry a valid seat ({@code room} and
 * {@code token} headers); the only subscription allowed is the player's own
 * view; clients may not SEND, which would otherwise go straight to the broker.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    static final String SUBSCRIBE_DESTINATION = "/user" + RoomBroadcaster.VIEW_DESTINATION;

    private final RoomService rooms;

    StompAuthInterceptor(RoomService rooms) {
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> {
                String room = accessor.getFirstNativeHeader("room");
                String token = accessor.getFirstNativeHeader("token");
                try {
                    String playerId = rooms.authenticateSeat(room, token);
                    accessor.setUser(new PlayerPrincipal(playerId, room.toUpperCase(Locale.ROOT)));
                } catch (RoomException e) {
                    throw new MessageDeliveryException(message, e.getMessage());
                }
            }
            case SUBSCRIBE -> {
                if (!(accessor.getUser() instanceof PlayerPrincipal) || !SUBSCRIBE_DESTINATION.equals(accessor.getDestination())) {
                    throw new MessageDeliveryException(message, "Only " + SUBSCRIBE_DESTINATION + " can be subscribed to.");
                }
            }
            case SEND -> throw new MessageDeliveryException(message, "Send moves through the HTTP API.");
            default -> { }
        }
        return message;
    }
}
