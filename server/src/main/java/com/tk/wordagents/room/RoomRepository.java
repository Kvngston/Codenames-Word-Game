package com.tk.wordagents.room;

import com.tk.wordagents.game.Phase;
import com.tk.wordagents.game.Room;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
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

    /** Rooms and the players in them, per phase, for rooms changed since {@code since}. */
    @Query("""
        select r.phase as phase, count(distinct r) as rooms, count(p) as players
        from Room r left join r.players p
        where r.updatedAt >= :since
        group by r.phase""")
    List<PhaseCount> countByPhase(Instant since);

    interface PhaseCount {
        Phase getPhase();
        long getRooms();
        long getPlayers();
    }

    /** Bulk delete; players, cards and clues go with it through ON DELETE CASCADE. */
    @Modifying
    @Query("delete from Room r where r.updatedAt < :cutoff")
    int deleteIdleSince(Instant cutoff);
}
