package com.tk.wordagents.room;

import com.tk.wordagents.game.Player;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlayerRepository extends JpaRepository<Player, String> {

    Optional<Player> findByTokenHash(String tokenHash);
}
