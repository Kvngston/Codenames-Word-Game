package com.tk.wordagents.room;

import com.tk.wordagents.game.Player;
import com.tk.wordagents.game.Room;
import com.tk.wordagents.room.RoomViews.RoomView;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /**
     * Connects and disconnects in one room within this window share one refresh.
     * When a server restart or a network blip drops a whole table, its players
     * reconnect together; refreshing per player sent every view once per reconnect.
     */
    static final Duration PRESENCE_BATCH = Duration.ofMillis(300);

    private static final Logger log = LoggerFactory.getLogger(RoomBroadcaster.class);

    private record Delivery(String playerId, RoomView view) {}

    private final RoomRepository rooms;
    private final Presence presence;
    private final SimpMessagingTemplate messaging;
    private final TransactionTemplate readOnly;
    /** Rooms with a presence refresh scheduled, and the sessions closing since it was. */
    private final Map<String, Set<String>> pendingPresence = new ConcurrentHashMap<>();
    // Off the STOMP inbound threads: a refresh reads the room and sends a view per player,
    // and running it there held up every other CONNECT and SUBSCRIBE behind it.
    private final ScheduledExecutorService presenceRefresher =
        Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("presence-", 1).daemon().factory());

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
        List<Delivery> deliveries = viewsFor(room, Set.of());
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
        if (event.getUser() instanceof PlayerPrincipal player) presenceChanged(player.roomCode(), null);
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() instanceof PlayerPrincipal player) presenceChanged(player.roomCode(), event.getSessionId());
    }

    @PreDestroy
    void stop() {
        presenceRefresher.shutdownNow();
    }

    private void presenceChanged(String code, String closingSessionId) {
        // compute and remove lock the room's entry, so a change lands in the batch
        // being flushed or schedules the next one; it is never dropped.
        pendingPresence.compute(code, (room, closing) -> {
            if (closing == null) {
                closing = new HashSet<>();
                presenceRefresher.schedule(() -> flushPresence(room), PRESENCE_BATCH.toMillis(), TimeUnit.MILLISECONDS);
            }
            if (closingSessionId != null) closing.add(closingSessionId);
            return closing;
        });
    }

    private void flushPresence(String code) {
        Set<String> closing = pendingPresence.remove(code);
        try {
            refresh(code, closing == null ? Set.of() : closing);
        } catch (RuntimeException e) {
            log.warn("Presence refresh failed for room {}", code, e);
        }
    }

    private void refresh(String code, Set<String> closingSessionIds) {
        List<Delivery> deliveries = readOnly.execute(status ->
            rooms.findById(code).map(room -> viewsFor(room, closingSessionIds)).orElse(List.of()));
        send(deliveries);
    }

    /**
     * Every player gets a delivery, not just those this instance sees as online:
     * another instance may hold a session the shared user registry hasn't
     * reported yet. Messages for players with no session anywhere are dropped.
     */
    private List<Delivery> viewsFor(Room room, Set<String> closingSessionIds) {
        return room.getPlayers().stream()
            .map(Player::getId)
            .map(id -> new Delivery(id, RoomViews.viewFor(room, id, other -> presence.isOnline(other, closingSessionIds))))
            .toList();
    }

    private void send(List<Delivery> deliveries) {
        // Resolves to each player's sessions on whichever instance holds them.
        deliveries.forEach(delivery -> messaging.convertAndSendToUser(delivery.playerId(), VIEW_DESTINATION, delivery.view()));
    }
}
