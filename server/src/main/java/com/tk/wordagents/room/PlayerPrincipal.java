package com.tk.wordagents.room;

import java.security.Principal;

/** The authenticated identity of a WebSocket session: one player's seat in one room. */
record PlayerPrincipal(String playerId, String roomCode) implements Principal {

    @Override
    public String getName() {
        return playerId;
    }
}
