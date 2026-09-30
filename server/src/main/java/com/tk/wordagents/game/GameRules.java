package com.tk.wordagents.game;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The rules of the game. Every permission check lives here, so a client can
 * never act outside its seat no matter what it sends.
 */
public final class GameRules {

    public static final int BOARD_SIZE = 25;
    private static final Random RANDOM = new SecureRandom();

    private GameRules() {}

    /** Deals a fresh board and resets the room to the lobby. Seats are kept. */
    public static void deal(Room room) {
        deal(room, RANDOM);
    }

    static void deal(Room room, Random random) {
        Team starting = random.nextBoolean() ? Team.RED : Team.BLUE;
        List<CardRole> roles = new ArrayList<>();
        for (int i = 0; i < 9; i++) roles.add(starting.cardRole());
        for (int i = 0; i < 8; i++) roles.add(starting.other().cardRole());
        for (int i = 0; i < 7; i++) roles.add(CardRole.NEUTRAL);
        roles.add(CardRole.ASSASSIN);
        Collections.shuffle(roles, random);
        List<String> words = new ArrayList<>(Words.ALL);
        Collections.shuffle(words, random);

        List<Card> cards = room.getCards();
        for (int position = cards.size(); position < BOARD_SIZE; position++) cards.add(new Card(room, position));
        for (Card card : cards) {
            // Card ids are random and independent of role, so they can go to every player.
            card.deal(UUID.randomUUID().toString(), words.get(card.getPosition()), roles.get(card.getPosition()));
        }

        room.setStartingTeam(starting);
        room.setActiveTeam(starting);
        room.setPhase(Phase.LOBBY);
        room.setClue(null);
        room.setPendingReview(null);
        room.setGuessesRemaining(Count.of(0));
        room.setTurnGuesses(0);
        room.getClues().clear();
        room.setWinner(null);
        room.setResultMessage("");
        room.setPenaltyRevealPending(false);
        room.setLastEvent("");
    }

    public static int remaining(Room room, Team team) {
        return (int) room.getCards().stream().filter(card -> card.getRole() == team.cardRole() && !card.isRevealed()).count();
    }

    public static int total(Room room, Team team) {
        return (int) room.getCards().stream().filter(card -> card.getRole() == team.cardRole()).count();
    }

    public static String roleLabel(CardRole role, Room room) {
        return switch (role) {
            case RED -> room.teamName(Team.RED);
            case BLUE -> room.teamName(Team.BLUE);
            case ASSASSIN -> "the assassin";
            case NEUTRAL -> "neutral";
        };
    }

    /** A clue that equals an unrevealed word, or a part of one, needs the opposing spymaster's ruling. */
    static boolean isQuestionableClue(Room room, String clue) {
        List<String> parts = Arrays.stream(clue.trim().toLowerCase(Locale.ROOT).split("[\\s-]+")).filter(s -> !s.isEmpty()).toList();
        return room.getCards().stream().filter(card -> !card.isRevealed()).anyMatch(card -> {
            String word = card.getWord().toLowerCase(Locale.ROOT);
            List<String> wordParts = Arrays.asList(word.split("[\\s-]+"));
            return parts.stream().anyMatch(part -> part.equals(word) || wordParts.contains(part));
        });
    }

    /** Why the table can't start yet, or empty when it's ready. */
    public static Optional<String> seatingProblem(Room room) {
        for (Team team : Team.values()) {
            List<Player> seated = room.getPlayers().stream().filter(player -> player.getTeam() == team).toList();
            if (seated.stream().noneMatch(player -> player.getSeat() == Seat.SPYMASTER)) return Optional.of(room.teamName(team) + " needs a spymaster.");
            if (seated.stream().noneMatch(player -> player.getSeat() == Seat.OPERATIVE)) return Optional.of(room.teamName(team) + " needs at least one operative.");
        }
        return Optional.empty();
    }

    /** Applies one player's action to the room, mutating it in place. */
    public static void apply(Room room, String playerId, GameAction action) {
        Player player = room.player(playerId).orElseThrow(() -> new GameException("You are not in this room."));
        Team active = room.getActiveTeam();
        boolean isHost = room.getHostPlayerId().equals(player.getId());
        boolean isActiveSpymaster = player.getSeat() == Seat.SPYMASTER && player.getTeam() == active;
        boolean isActiveOperative = player.getSeat() == Seat.OPERATIVE && player.getTeam() == active;

        switch (action) {
            case GameAction.TakeSeat(Team team, Seat seat) -> {
                ensure(team != null && seat != null, "Pick a team and a seat.");
                if (room.getPhase() != Phase.LOBBY) {
                    // Joining late is fine as an operative; nobody can pick up the secret map mid-game.
                    ensure(player.getSeat() == null && seat == Seat.OPERATIVE, "Seats are locked while a game is running. Late arrivals can join as operatives.");
                }
                if (seat == Seat.SPYMASTER) {
                    Optional<Player> holder = room.getPlayers().stream()
                        .filter(other -> other.getTeam() == team && other.getSeat() == Seat.SPYMASTER)
                        .findFirst();
                    ensure(holder.isEmpty() || holder.get() == player, room.teamName(team) + " already has a spymaster.");
                }
                player.setTeam(team);
                player.setSeat(seat);
            }

            case GameAction.LeaveSeat() -> {
                ensure(room.getPhase() == Phase.LOBBY, "Seats are locked while a game is running.");
                player.setTeam(null);
                player.setSeat(null);
            }

            case GameAction.RenameTeam(Team team, String name) -> {
                ensure(isHost, "Only the host can rename teams.");
                ensure(room.getPhase() == Phase.LOBBY, "Team names are locked once the game starts.");
                ensure(team != null, "Pick a team to rename.");
                String clean = name == null ? "" : name.trim();
                if (clean.length() > 18) clean = clean.substring(0, 18);
                room.setTeamName(team, clean.isEmpty() ? (team == Team.RED ? "Red" : "Blue") : clean);
            }

            case GameAction.Start() -> {
                ensure(isHost, "Only the host can deal the words.");
                ensure(room.getPhase() == Phase.LOBBY, "The game has already started.");
                seatingProblem(room).ifPresent(problem -> { throw new GameException(problem); });
                room.setPhase(Phase.CLUE);
                room.setActiveTeam(room.getStartingTeam());
                room.setLastEvent(room.teamName(room.getStartingTeam()) + " goes first.");
            }

            case GameAction.GiveClue(String rawWord, Count number) -> {
                ensure(room.getPhase() == Phase.CLUE, "It is not time for a clue.");
                ensure(isActiveSpymaster, "Only the " + room.teamName(active) + " spymaster can give this clue.");
                ensure(room.pendingReview().isEmpty(), "Your last clue is still being reviewed.");
                ensure(!room.isPenaltyRevealPending(), "Reveal or skip your penalty card first.");
                String word = rawWord == null ? "" : rawWord.trim().toUpperCase(Locale.ROOT);
                ensure(!word.isEmpty(), "Enter a one-word clue first.");
                ensure(!word.matches(".*\\s.*"), "Keep it to one word. A hyphenated word is okay.");
                ensure(word.length() <= 32, "That clue is too long.");
                ensure(number != null && (number.unlimited() || (number.value() >= 0 && number.value() <= 9)), "Pick a number from 0 to 9, or unlimited.");
                ActiveClue clue = new ActiveClue(active, word, number);
                if (isQuestionableClue(room, word)) {
                    room.setPendingReview(clue);
                    room.setLastEvent(room.teamName(active) + "'s clue is waiting on the " + room.teamName(active.other()) + " spymaster.");
                    return;
                }
                publishClue(room, clue);
                room.setLastEvent(room.teamName(active) + " clue: " + word + " " + number + ".");
            }

            case GameAction.ReviewClue(boolean uphold) -> {
                ActiveClue review = room.pendingReview().orElse(null);
                ensure(room.getPhase() == Phase.CLUE && review != null, "There is no clue to review.");
                Team judge = review.team().other();
                ensure(player.getSeat() == Seat.SPYMASTER && player.getTeam() == judge, "Only the " + room.teamName(judge) + " spymaster can rule on this clue.");
                if (!uphold) {
                    publishClue(room, review);
                    room.setLastEvent("Clue accepted. " + room.teamName(review.team()) + " clue: " + review.word() + " " + review.number() + ".");
                    return;
                }
                endTurn(room, remaining(room, judge) > 0);
                room.setLastEvent(room.teamName(review.team()) + "'s clue was ruled invalid. " + room.teamName(judge) + " takes the turn.");
            }

            case GameAction.PenaltyReveal(String cardId) -> {
                ensure(room.getPhase() == Phase.CLUE && room.isPenaltyRevealPending(), "There is no penalty reveal to make.");
                ensure(isActiveSpymaster, "Only the active spymaster can make the penalty reveal.");
                Card card = findCard(room, cardId);
                ensure(card.getRole() == active.cardRole(), "The penalty reveal must be a " + room.teamName(active) + " card.");
                card.reveal();
                room.setPenaltyRevealPending(false);
                if (remaining(room, active) == 0) {
                    finish(room, active, room.teamName(active) + " found the final friendly card on a clue penalty.");
                }
                room.setLastEvent(card.getWord() + " revealed for " + room.teamName(active) + " on a clue penalty.");
            }

            case GameAction.SkipPenalty() -> {
                ensure(room.getPhase() == Phase.CLUE && room.isPenaltyRevealPending(), "There is no penalty reveal to skip.");
                ensure(isActiveSpymaster, "Only the active spymaster can skip the penalty reveal.");
                room.setPenaltyRevealPending(false);
            }

            case GameAction.Guess(String cardId) -> {
                ensure(room.getPhase() == Phase.GUESSING, "Wait for your spymaster’s clue.");
                ensure(isActiveOperative, "Only " + room.teamName(active) + " operatives can guess right now.");
                Card card = findCard(room, cardId);
                Team rival = active.other();
                card.reveal();
                room.setLastEvent(player.getName() + " picked " + card.getWord() + ": " + roleLabel(card.getRole(), room) + ".");

                if (card.getRole() == CardRole.ASSASSIN) {
                    finish(room, rival, room.teamName(active) + " uncovered the assassin.");
                } else if (card.getRole() == active.cardRole()) {
                    if (remaining(room, active) == 0) {
                        finish(room, active, room.teamName(active) + " found every one of its agents.");
                        return;
                    }
                    room.setTurnGuesses(room.getTurnGuesses() + 1);
                    Count budget = room.getGuessesRemaining();
                    if (!budget.unlimited()) {
                        Count next = Count.of(Math.max(0, budget.value() - 1));
                        room.setGuessesRemaining(next);
                        if (next.value() == 0) endTurn(room, false);
                    }
                } else if (card.getRole() == rival.cardRole() && remaining(room, rival) == 0) {
                    finish(room, rival, room.teamName(rival) + " revealed its final agent.");
                } else {
                    endTurn(room, false);
                }
            }

            case GameAction.EndTurn() -> {
                ensure(room.getPhase() == Phase.GUESSING, "There is no guessing turn to end.");
                ensure(isActiveOperative, "Only " + room.teamName(active) + " operatives can end this turn.");
                ensure(room.getTurnGuesses() >= 1, "Make at least one correct guess before stopping.");
                endTurn(room, false);
                room.setLastEvent(player.getName() + " ended " + room.teamName(active) + "'s turn.");
            }

            case GameAction.NewGame() -> {
                ensure(isHost, "Only the host can deal a new game.");
                deal(room);
                room.setLastEvent("A fresh word map is on the table. Check your seats, then deal.");
            }
        }
    }

    private static void publishClue(Room room, ActiveClue clue) {
        room.setPhase(Phase.GUESSING);
        room.setClue(clue);
        Count number = clue.number();
        room.setGuessesRemaining(number.unlimited() || number.value() == 0 ? Count.UNLIMITED : Count.of(number.value() + 1));
        room.setTurnGuesses(0);
        room.setPendingReview(null);
        room.getClues().add(new Clue(room, room.getClues().size() + 1, clue));
    }

    private static void endTurn(Room room, boolean penaltyRevealPending) {
        room.setActiveTeam(room.getActiveTeam().other());
        room.setPhase(Phase.CLUE);
        room.setGuessesRemaining(Count.of(0));
        room.setTurnGuesses(0);
        room.setPendingReview(null);
        room.setPenaltyRevealPending(penaltyRevealPending);
    }

    private static void finish(Room room, Team winner, String message) {
        room.setPhase(Phase.FINISHED);
        room.setWinner(winner);
        room.setResultMessage(message);
        room.setPenaltyRevealPending(false);
        room.setPendingReview(null);
        room.setGuessesRemaining(Count.of(0));
    }

    private static Card findCard(Room room, String cardId) {
        Card card = room.getCards().stream()
            .filter(item -> item.getCardId().equals(cardId))
            .findFirst()
            .orElseThrow(() -> new GameException("That card is not on this board."));
        ensure(!card.isRevealed(), "That card is already revealed.");
        return card;
    }

    private static void ensure(boolean condition, String message) {
        if (!condition) throw new GameException(message);
    }
}
