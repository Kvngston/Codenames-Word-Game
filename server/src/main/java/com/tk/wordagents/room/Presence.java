package com.tk.wordagents.room;

import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * A player is online while they hold a WebSocket session on any server instance.
 * The user registry is shared between instances through the broker relay.
 */
@Component
class Presence {

    private final SimpUserRegistry users;

    Presence(SimpUserRegistry users) {
        this.users = users;
    }

    boolean isOnline(String playerId) {
        return isOnline(playerId, null);
    }

    /** As {@link #isOnline(String)}, ignoring a session that is in the middle of closing. */
    boolean isOnline(String playerId, String closingSessionId) {
        SimpUser user = users.getUser(playerId);
        return user != null && user.getSessions().stream().anyMatch(session -> !session.getId().equals(closingSessionId));
    }
}
