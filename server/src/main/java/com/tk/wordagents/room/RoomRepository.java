package com.tk.wordagents.room;

import com.tk.wordagents.game.Room;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RoomRepository extends JpaRepository<Room, String> {

    /** Locks the room row (SELECT … FOR UPDATE) so moves in one room are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.code = :code")
    Optional<Room> findForUpdate(String code);

    /** Bulk delete; players, cards and clues go with it through ON DELETE CASCADE. */
    @Modifying
    @Query("delete from Room r where r.updatedAt < :cutoff")
    int deleteIdleSince(Instant cutoff);
}
