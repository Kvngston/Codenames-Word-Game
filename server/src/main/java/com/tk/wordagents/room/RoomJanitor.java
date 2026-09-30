package com.tk.wordagents.room;

import com.tk.wordagents.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deletes rooms nobody has touched for a while. Safe to run on every instance. */
@Component
class RoomJanitor {

    private static final Logger log = LoggerFactory.getLogger(RoomJanitor.class);

    private final RoomRepository rooms;
    private final Duration idle;

    RoomJanitor(RoomRepository rooms, AppProperties properties) {
        this.rooms = rooms;
        this.idle = Duration.ofHours(properties.roomIdleHours());
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    @Transactional
    void sweep() {
        int removed = rooms.deleteIdleSince(Instant.now().minus(idle));
        if (removed > 0) log.info("Closed {} idle rooms", removed);
    }
}
