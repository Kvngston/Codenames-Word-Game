package com.tk.wordagents.room;

import com.tk.wordagents.game.GameAction;
import com.tk.wordagents.game.GameRules;
import com.tk.wordagents.game.Phase;
import com.tk.wordagents.game.Player;
import com.tk.wordagents.game.Room;
import com.tk.wordagents.room.RoomViews.RoomView;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    public record SeatGrant(String code, String playerId, String token) {}

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_PLAYERS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoomRepository rooms;
    private final PlayerRepository players;
    private final Presence presence;
    private final RoomBroadcaster broadcaster;

    RoomService(RoomRepository rooms, PlayerRepository players, Presence presence, RoomBroadcaster broadcaster) {
        this.rooms = rooms;
        this.players = players;
        this.presence = presence;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public SeatGrant createRoom(String hostName) {
        String code;
        do {
            code = newCode();
        } while (rooms.existsById(code));
        Room room = new Room(code);
        GameRules.deal(room);
        String token = SeatTokens.generate();
        Player host = addPlayer(room, hostName, token);
        room.setHostPlayerId(host.getId());
        rooms.save(room);
        return new SeatGrant(code, host.getId(), token);
    }

    @Transactional
    public SeatGrant join(String code, String name) {
        Room room = lock(code);
        if (room.getPlayers().size() >= MAX_PLAYERS) throw new RoomException(HttpStatus.CONFLICT, "This room is full.");
        String token = SeatTokens.generate();
        Player player = addPlayer(room, name, token);
        changed(room);
        return new SeatGrant(room.getCode(), player.getId(), token);
    }

    @Transactional(readOnly = true)
    public RoomView view(String code, String token) {
        Room room = rooms.findById(normalize(code)).orElseThrow(RoomException::notFound);
        return RoomViews.viewFor(room, authenticate(room, token).getId(), presence::isOnline);
    }

    @Transactional
    public RoomView act(String code, String token, GameAction action) {
        Room room = lock(code);
        Player player = authenticate(room, token);
        GameRules.apply(room, player.getId(), action);
        changed(room);
        return RoomViews.viewFor(room, player.getId(), presence::isOnline);
    }

    @Transactional
    public void leave(String code, String token) {
        Room room = lock(code);
        Player player = authenticate(room, token);
        if (room.getPhase() != Phase.LOBBY && player.isSeated()) {
            throw new RoomException(HttpStatus.CONFLICT, "You can leave once this game ends, or when the host deals a new one.");
        }
        room.getPlayers().remove(player);
        if (room.getPlayers().isEmpty()) {
            rooms.delete(room);
            return;
        }
        if (room.getHostPlayerId().equals(player.getId())) room.setHostPlayerId(room.getPlayers().getFirst().getId());
        changed(room);
    }

    /** Resolves a WebSocket CONNECT to the player it belongs to. */
    @Transactional(readOnly = true)
    public String authenticateSeat(String code, String token) {
        if (code == null || token == null) throw RoomException.badSeat();
        return players.findByTokenHash(SeatTokens.hash(token))
            .filter(player -> player.getRoom().getCode().equals(normalize(code)))
            .map(Player::getId)
            .orElseThrow(RoomException::badSeat);
    }

    private Room lock(String code) {
        return rooms.findForUpdate(normalize(code)).orElseThrow(RoomException::notFound);
    }

    private void changed(Room room) {
        room.touch();
        broadcaster.afterCommit(room);
    }

    private static Player authenticate(Room room, String token) {
        if (token == null) throw RoomException.badSeat();
        String hash = SeatTokens.hash(token);
        return room.getPlayers().stream()
            .filter(player -> player.getTokenHash().equals(hash))
            .findFirst()
            .orElseThrow(RoomException::badSeat);
    }

    private static Player addPlayer(Room room, String name, String token) {
        int order = room.getPlayers().stream().mapToInt(Player::getJoinOrder).max().orElse(-1) + 1;
        Player player = new Player(room, UUID.randomUUID().toString(), SeatTokens.hash(token), name.trim(), order);
        room.getPlayers().add(player);
        return player;
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder(5);
        for (int i = 0; i < 5; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return code.toString();
    }

    private static String normalize(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT);
    }
}
