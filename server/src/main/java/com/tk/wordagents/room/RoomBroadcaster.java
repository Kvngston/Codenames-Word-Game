package com.tk.wordagents.room;

import com.tk.wordagents.game.Player;
import com.tk.wordagents.game.Room;
import com.tk.wordagents.room.RoomViews.RoomView;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/** Pushes each connected player their own view whenever their room changes. */
@Component
class RoomBroadcaster {

    static final String VIEW_DESTINATION = "/topic/view";

    private record Delivery(String playerId, RoomView view) {}

    private final RoomRepository rooms;
    private final Presence presence;
    private final SimpMessagingTemplate messaging;
    private final TransactionTemplate readOnly;

    RoomBroadcaster(RoomRepository rooms, Presence presence, SimpMessagingTemplate messaging, PlatformTransactionManager transactions) {
        this.rooms = rooms;
        this.presence = presence;
        this.messaging = messaging;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    /**
     * Called inside a move's transaction. Views are built now, from the room
     * already loaded under its lock, and sent only once the move commits, so
     * nobody sees a move that rolled back and no second connection is needed.
     */
    void afterCommit(Room room) {
        List<Delivery> deliveries = viewsFor(room, null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(deliveries);
            }
        });
    }

    /** A new subscriber gets their view, and everyone else sees them come online. */
    @EventListener
    void onSubscribe(SessionSubscribeEvent event) {
        if (event.getUser() instanceof PlayerPrincipal player) refresh(player.roomCode(), null);
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() instanceof PlayerPrincipal player) refresh(player.roomCode(), event.getSessionId());
    }

    private void refresh(String code, String closingSessionId) {
        List<Delivery> deliveries = readOnly.execute(status ->
            rooms.findById(code).map(room -> viewsFor(room, closingSessionId)).orElse(List.of()));
        send(deliveries);
    }

    /**
     * Every player gets a delivery, not just those this instance sees as online:
     * another instance may hold a session the shared user registry hasn't
     * reported yet. Messages for players with no session anywhere are dropped.
     */
    private List<Delivery> viewsFor(Room room, String closingSessionId) {
        return room.getPlayers().stream()
            .map(Player::getId)
            .map(id -> new Delivery(id, RoomViews.viewFor(room, id, other -> presence.isOnline(other, closingSessionId))))
            .toList();
    }

    private void send(List<Delivery> deliveries) {
        // Resolves to each player's sessions on whichever instance holds them.
        deliveries.forEach(delivery -> messaging.convertAndSendToUser(delivery.playerId(), VIEW_DESTINATION, delivery.view()));
    }
}
