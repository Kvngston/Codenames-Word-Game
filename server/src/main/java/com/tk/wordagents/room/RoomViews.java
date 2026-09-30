package com.tk.wordagents.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tk.wordagents.game.ActiveClue;
import com.tk.wordagents.game.CardRole;
import com.tk.wordagents.game.Count;
import com.tk.wordagents.game.GameRules;
import com.tk.wordagents.game.Phase;
import com.tk.wordagents.game.Player;
import com.tk.wordagents.game.Room;
import com.tk.wordagents.game.Seat;
import com.tk.wordagents.game.Team;
import java.util.List;
import java.util.function.Predicate;

/** What a single player's device receives. Mirrors {@code RoomView} in lib/game-core. */
public final class RoomViews {

    private RoomViews() {}

    public record TeamPair<T>(T red, T blue) {}

    public record CardView(String id, String word, boolean revealed, CardRole role) {}

    public record PlayerView(String id, String name, Team team, Seat seat, boolean connected, @JsonProperty("isHost") boolean isHost) {}

    public record ClueRecord(Team team, String word, Count number, int turn) {}

    public record RoomView(
        String code,
        long version,
        PlayerView you,
        List<PlayerView> players,
        TeamPair<String> teamNames,
        Phase phase,
        Team startingTeam,
        Team activeTeam,
        List<CardView> cards,
        TeamPair<Integer> remaining,
        TeamPair<Integer> totals,
        ActiveClue clue,
        Count guessesRemaining,
        int turnGuesses,
        List<ClueRecord> clueHistory,
        Team winner,
        String resultMessage,
        boolean penaltyRevealPending,
        boolean reviewPending,
        ActiveClue review,
        String lastEvent) {}

    /**
     * Builds the only state this player's device ever receives. Unrevealed card
     * roles (the secret map) go to spymasters only, so an operative can't recover
     * them from devtools or the network.
     */
    public static RoomView viewFor(Room room, String viewerId, Predicate<String> isOnline) {
        Player viewer = room.player(viewerId).orElseThrow(() -> new IllegalArgumentException("Player " + viewerId + " is not in room " + room.getCode()));
        boolean isSpymaster = viewer.getSeat() == Seat.SPYMASTER;
        boolean seesMap = isSpymaster && room.getPhase() != Phase.LOBBY;
        boolean gameOver = room.getPhase() == Phase.FINISHED;
        List<PlayerView> players = room.getPlayers().stream().map(player -> playerView(room, player, isOnline)).toList();

        return new RoomView(
            room.getCode(),
            room.getRevision(),
            playerView(room, viewer, isOnline),
            players,
            new TeamPair<>(room.teamName(Team.RED), room.teamName(Team.BLUE)),
            room.getPhase(),
            room.getStartingTeam(),
            room.getActiveTeam(),
            room.getCards().stream()
                .map(card -> new CardView(card.getCardId(), card.getWord(), card.isRevealed(),
                    card.isRevealed() || seesMap || gameOver ? card.getRole() : null))
                .toList(),
            new TeamPair<>(GameRules.remaining(room, Team.RED), GameRules.remaining(room, Team.BLUE)),
            new TeamPair<>(GameRules.total(room, Team.RED), GameRules.total(room, Team.BLUE)),
            room.clue().orElse(null),
            room.getGuessesRemaining(),
            room.getTurnGuesses(),
            room.getClues().stream().map(clue -> new ClueRecord(clue.getTeam(), clue.getWord(), clue.getNumber(), clue.getTurn())).toList(),
            room.getWinner(),
            room.getResultMessage(),
            room.isPenaltyRevealPending(),
            room.pendingReview().isPresent(),
            isSpymaster ? room.pendingReview().orElse(null) : null,
            room.getLastEvent());
    }

    private static PlayerView playerView(Room room, Player player, Predicate<String> isOnline) {
        return new PlayerView(player.getId(), player.getName(), player.getTeam(), player.getSeat(),
            isOnline.test(player.getId()), room.getHostPlayerId().equals(player.getId()));
    }
}
