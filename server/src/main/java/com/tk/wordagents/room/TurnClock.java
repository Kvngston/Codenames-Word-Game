package com.tk.wordagents.room;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ends turns whose time ran out. Safe to run on every instance. */
@Component
class TurnClock {

    private static final Logger log = LoggerFactory.getLogger(TurnClock.class);

    private final RoomRepository rooms;
    private final RoomService service;

    TurnClock(RoomRepository rooms, RoomService service) {
        this.rooms = rooms;
        this.service = service;
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.SECONDS)
    void tick() {
        for (String code : rooms.findTurnsEndedBy(Instant.now())) {
            try {
                service.expireTurn(code);
            } catch (RuntimeException e) {
                log.warn("Could not end the turn in room {}", code, e);
            }
        }
    }
}
