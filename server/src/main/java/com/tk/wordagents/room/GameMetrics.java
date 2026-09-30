package com.tk.wordagents.room;

import com.tk.wordagents.game.GameAction;
import com.tk.wordagents.game.Phase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Game-level metrics for the monitoring dashboard: rooms and players per phase
 * (read from MySQL, so every instance reports the same totals), connected
 * players, and counters for rooms, games and moves.
 */
@Component
class GameMetrics {

    /** A room counts as active if anyone has joined, moved or left within this window. */
    static final Duration ACTIVE_WINDOW = Duration.ofMinutes(15);

    private record Totals(AtomicLong rooms, AtomicLong players) {}

    private final RoomRepository rooms;
    private final MeterRegistry registry;
    private final Map<Phase, Totals> all = new EnumMap<>(Phase.class);
    private final Map<Phase, Totals> active = new EnumMap<>(Phase.class);
    private final Counter roomsCreated;
    private final Counter playersJoined;
    private final Counter gamesStarted;

    GameMetrics(RoomRepository rooms, SimpUserRegistry users, MeterRegistry registry) {
        this.rooms = rooms;
        this.registry = registry;
        for (Phase phase : Phase.values()) {
            all.put(phase, totals(phase, "all"));
            active.put(phase, totals(phase, "active"));
        }
        // The user registry is shared between instances through the broker relay.
        Gauge.builder("wordagents.players.online", users, SimpUserRegistry::getUserCount)
            .description("Players with an open WebSocket session")
            .register(registry);
        // Not "rooms.created": Prometheus reserves the _created suffix, which would clash with wordagents_rooms.
        this.roomsCreated = Counter.builder("wordagents.rooms.opened").register(registry);
        this.playersJoined = Counter.builder("wordagents.players.joined").register(registry);
        this.gamesStarted = Counter.builder("wordagents.games.started").register(registry);
    }

    private Totals totals(Phase phase, String scope) {
        Totals totals = new Totals(new AtomicLong(), new AtomicLong());
        String tag = phase.json();
        Gauge.builder("wordagents.rooms", totals.rooms(), AtomicLong::get)
            .description("Rooms by phase; scope=active only counts rooms changed in the last 15 minutes")
            .tags("phase", tag, "scope", scope)
            .register(registry);
        Gauge.builder("wordagents.room.players", totals.players(), AtomicLong::get)
            .description("Players sitting in rooms, by the room's phase")
            .tags("phase", tag, "scope", scope)
            .register(registry);
        return totals;
    }

    /** Two grouped queries every 15 seconds, so Prometheus scrapes never touch the database. */
    @Scheduled(fixedDelay = 15, initialDelay = 5, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    void refresh() {
        publish(all, rooms.countByPhase(Instant.EPOCH));
        publish(active, rooms.countByPhase(Instant.now().minus(ACTIVE_WINDOW)));
    }

    private static void publish(Map<Phase, Totals> gauges, List<RoomRepository.PhaseCount> rows) {
        Map<Phase, RoomRepository.PhaseCount> byPhase = new EnumMap<>(Phase.class);
        rows.forEach(row -> byPhase.put(row.getPhase(), row));
        gauges.forEach((phase, totals) -> {
            RoomRepository.PhaseCount row = byPhase.get(phase);
            totals.rooms().set(row == null ? 0 : row.getRooms());
            totals.players().set(row == null ? 0 : row.getPlayers());
        });
    }

    void roomCreated() {
        roomsCreated.increment();
    }

    void playerJoined() {
        playersJoined.increment();
    }

    /** Counts a move and, from the phase change it caused, games starting and ending. */
    void action(GameAction action, Phase before, Phase after, String outcome) {
        registry.counter("wordagents.game.actions", "action", actionName(action), "outcome", outcome).increment();
        if (before == Phase.LOBBY && after == Phase.CLUE) gamesStarted.increment();
        if (before != Phase.FINISHED && after == Phase.FINISHED) registry.counter("wordagents.games.finished").increment();
    }

    /** The JSON {@code type} of an action: {@code GiveClue} is {@code give-clue}. */
    private static String actionName(GameAction action) {
        if (action == null) return "none";
        return action.getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
