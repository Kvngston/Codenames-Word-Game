package com.tk.wordagents.game;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The rules of the game. Every permission check lives here, so a client can
 * never act outside its seat no matter what it sends.
 */
public final class GameRules {

    public static final int BOARD_SIZE = 25;
    public static final int MAX_CUSTOM_WORDS = 200;
    /** More genres than this and the board stops hanging together: the assassin ends up unrelated to every team word. */
    public static final int MAX_PACKS = 2;
    /** How long a spymaster has to give a clue, and the operatives to guess on it, until the host changes it. */
    public static final Duration DEFAULT_SPYMASTER_TIME = Duration.ofMinutes(3);
    public static final Duration DEFAULT_OPERATIVE_TIME = Duration.ofMinutes(5);
    public static final Duration MIN_TURN_TIME = Duration.ofSeconds(30);
    public static final Duration MAX_TURN_TIME = Duration.ofMinutes(10);
    private static final Random RANDOM = new SecureRandom();
    /** One word: letters or digits, apostrophes inside it (DON'T), and at most one hyphen (X-RAY). */
    private static final Pattern ONE_WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*(?:-[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*)?");

    private GameRules() {}

    /** Deals a fresh board and resets the room to the lobby. Seats are kept. */
    public static void deal(Room room, WordLibrary library) {
        deal(room, library, RANDOM);
    }

    static void deal(Room room, WordLibrary library, Random random) {
        Team starting = random.nextBoolean() ? Team.RED : Team.BLUE;
        List<BoardDealer.Dealt> dealt = BoardDealer.deal(room, library, starting, random);

        List<Card> cards = room.getCards();
        for (int position = cards.size(); position < BOARD_SIZE; position++) cards.add(new Card(room, position));
        for (Card card : cards) {
            // Card ids are random and independent of role, so they can go to every player.
            BoardDealer.Dealt deal = dealt.get(card.getPosition());
            card.deal(UUID.randomUUID().toString(), deal.word(), deal.role());
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
        room.setTurnEndsAt(null);
        clearHighlights(room);
    }

    private static void clearHighlights(Room room) {
        room.getPlayers().forEach(player -> player.setHighlights(List.of()));
    }

    /** Every distinct word in these packs that isn't already a custom word. Missing packs are skipped. */
    private static Set<String> packWords(List<String> packs, List<String> custom, WordLibrary library) {
        Set<String> words = new LinkedHashSet<>();
        packs.forEach(id -> library.words(id).ifPresent(words::addAll));
        custom.forEach(words::remove);
        return words;
    }

    /** Built-in pack ids are lower case, saved-pack codes upper case. */
    private static String packKey(String raw) {
        String id = raw == null ? "" : raw.trim();
        return WordPacks.find(id).map(WordPacks.Pack::id).orElse(id.toUpperCase(Locale.ROOT));
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
    /**
     * Why a clue can't be given at all: it is an unrevealed word on the board, a
     * part of one (SCUBA for SCUBA DIVER), or one written without its spaces.
     */
    static Optional<String> boardConflict(Room room, String clue) {
        List<String> parts = parts(clue);
        String joined = String.join("", parts);
        for (Card card : unrevealed(room)) {
            List<String> wordParts = parts(card.getWord());
            if (joined.equals(String.join("", wordParts))) return Optional.of("“" + card.getWord() + "” is on the board. Pick a clue that isn’t one of the words.");
            for (String part : parts) {
                if (wordParts.contains(part)) return Optional.of("“" + part.toUpperCase(Locale.ROOT) + "” is part of “" + card.getWord() + "” on the board. Pick a different clue.");
            }
        }
        return Optional.empty();
    }

    /**
     * A clue that contains a board word, or sits inside one (SHARKS, FIREMAN),
     * needs the opposing spymaster's ruling. Exact matches never get this far.
     */
    static boolean isQuestionableClue(Room room, String clue) {
        String joined = String.join("", parts(clue));
        return unrevealed(room).stream().map(card -> String.join("", parts(card.getWord()))).anyMatch(word -> {
            boolean wordIsShorter = word.length() <= joined.length();
            String shorter = wordIsShorter ? word : joined;
            String longer = wordIsShorter ? joined : word;
            return shorter.length() >= 3 && longer.contains(shorter);
        });
    }

    private static List<String> parts(String text) {
        return Arrays.stream(text.trim().toLowerCase(Locale.ROOT).split("[\\s-]+")).filter(part -> !part.isEmpty()).toList();
    }

    private static List<Card> unrevealed(Room room) {
        return room.getCards().stream().filter(card -> !card.isRevealed()).toList();
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
    public static void apply(Room room, String playerId, GameAction action, WordLibrary library) {
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

            case GameAction.SetWords(List<String> rawPacks, List<String> rawCustom) -> {
                ensure(isHost, "Only the host can choose the words.");
                ensure(room.getPhase() == Phase.LOBBY, "The word list is locked once the game starts.");
                List<String> custom = WordLists.cleanAll(rawCustom == null ? List.of() : rawCustom);
                ensure(custom.size() <= MAX_CUSTOM_WORDS, "Keep custom words to " + MAX_CUSTOM_WORDS + " or fewer.");
                List<String> packs = new ArrayList<>();
                for (String raw : rawPacks == null ? List.<String>of() : rawPacks) {
                    String id = packKey(raw);
                    if (id.isEmpty() || packs.contains(id)) continue;
                    ensure(library.words(id).isPresent(), "There's no word pack with the code " + id + ".");
                    packs.add(id);
                }
                // A room set up before the cap may hold more; let the host switch packs off on the way down.
                ensure(packs.size() <= MAX_PACKS || packs.size() < room.getWordPacks().size(), "Pick up to " + MAX_PACKS + " packs.");
                int available = custom.size() + packWords(packs, custom, library).size();
                ensure(available >= BOARD_SIZE, available == 0
                    ? "Pick a pack or add some words."
                    : "That's only " + available + " words. Add " + (BOARD_SIZE - available) + " more, or pick a pack.");
                room.setWordPacks(packs);
                room.setCustomWords(custom);
                deal(room, library);
                room.setLastEvent("New words are on the table: " + room.getPoolSize() + " in the pool.");
            }

            case GameAction.SetTimer(boolean on, Integer spymasterSeconds, Integer operativeSeconds) -> {
                ensure(isHost, "Only the host can set the turn timer.");
                ensure(room.getPhase() == Phase.LOBBY, "The turn timer is locked once the game starts.");
                Duration spymaster = turnTime(spymasterSeconds, room.getSpymasterTime());
                Duration operative = turnTime(operativeSeconds, room.getOperativeTime());
                room.setTurnTimer(on);
                room.setSpymasterTime(spymaster);
                room.setOperativeTime(operative);
                room.setLastEvent(on ? "Turn timer on: " + clock(spymaster) + " per clue, " + clock(operative) + " to guess." : "Turn timer off.");
            }

            case GameAction.Start() -> {
                ensure(isHost, "Only the host can deal the words.");
                ensure(room.getPhase() == Phase.LOBBY, "The game has already started.");
                seatingProblem(room).ifPresent(problem -> { throw new GameException(problem); });
                room.setPhase(Phase.CLUE);
                room.setActiveTeam(room.getStartingTeam());
                startClock(room, room.getSpymasterTime());
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
                ensure(ONE_WORD.matcher(word).matches(), "Keep it to one word: no symbols like _ . / or +, and at most one hyphen.");
                ensure(word.length() <= 32, "That clue is too long.");
                ensure(number != null && (number.unlimited() || (number.value() >= 0 && number.value() <= 9)), "Pick a number from 0 to 9, or unlimited.");
                boardConflict(room, word).ifPresent(problem -> { throw new GameException(problem); });
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

            case GameAction.Guess(String cardId) -> guess(room, player, List.of(cardId));

            case GameAction.Guesses(List<String> cardIds) -> guess(room, player, cardIds == null ? List.of() : cardIds);

            case GameAction.SetHighlights(List<String> rawIds) -> {
                ensure(room.getPhase() == Phase.GUESSING, "Highlights are for the guessing turn.");
                // Spymasters never highlight: it would give the map away.
                ensure(isActiveOperative, "Only " + room.teamName(active) + " operatives can highlight words right now.");
                List<String> ids = rawIds == null ? List.of() : rawIds.stream().distinct().toList();
                ids.forEach(id -> findCard(room, id));
                player.setHighlights(ids);
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
                deal(room, library);
                room.setLastEvent("A fresh word map is on the table. Check your seats, then deal.");
            }
        }
    }

    /**
     * Ends a turn whose time ran out. A spymaster out of time loses the turn,
     * operatives out of time stop guessing. A clue still under review went in on
     * time, so the rival spymaster's silence counts as accepting it.
     * Returns false when the clock hasn't run out.
     */
    public static boolean expireTurn(Room room, Instant now) {
        Instant endsAt = room.getTurnEndsAt();
        if (endsAt == null || now.isBefore(endsAt)) return false;
        Team active = room.getActiveTeam();
        switch (room.getPhase()) {
            case CLUE -> {
                ActiveClue review = room.pendingReview().orElse(null);
                if (review != null) {
                    publishClue(room, review);
                    room.setLastEvent("Time ran out on the review, so the clue stands. " + room.teamName(active) + " clue: " + review.word() + " " + review.number() + ".");
                } else {
                    endTurn(room, false);
                    room.setLastEvent("The " + room.teamName(active) + " spymaster ran out of time. " + room.teamName(active.other()) + " takes the turn.");
                }
            }
            case GUESSING -> {
                endTurn(room, false);
                room.setLastEvent("Time's up for " + room.teamName(active) + ". " + room.teamName(active.other()) + " takes the turn.");
            }
            case LOBBY, FINISHED -> room.setTurnEndsAt(null);
        }
        return true;
    }

    /**
     * Reveals the operative's picks in the order they were chosen, stopping as
     * soon as one ends the turn or the game. Later picks are left unrevealed.
     */
    private static void guess(Room room, Player player, List<String> cardIds) {
        Team active = room.getActiveTeam();
        ensure(room.getPhase() == Phase.GUESSING, "Wait for your spymaster’s clue.");
        ensure(player.getSeat() == Seat.OPERATIVE && player.getTeam() == active, "Only " + room.teamName(active) + " operatives can guess right now.");
        ensure(!cardIds.isEmpty(), "Pick at least one word.");
        ensure(cardIds.stream().distinct().count() == cardIds.size(), "Each word can only be picked once.");
        List<Card> cards = cardIds.stream().map(id -> findCard(room, id)).toList();
        Count budget = room.getGuessesRemaining();
        if (!budget.unlimited() && cards.size() > budget.value()) {
            throw new GameException("This clue has " + budget.value() + (budget.value() == 1 ? " guess" : " guesses") + " left. Pick fewer words.");
        }

        List<String> picks = new ArrayList<>();
        for (Card card : cards) {
            reveal(room, card, active);
            picks.add(card.getWord() + ": " + roleLabel(card.getRole(), room));
            if (room.getPhase() != Phase.GUESSING || room.getActiveTeam() != active) break;
        }
        int dropped = cards.size() - picks.size();
        room.setLastEvent(player.getName() + " picked " + String.join(", ", picks) + "."
            + (dropped == 0 ? "" : " The turn ended before the other " + (dropped == 1 ? "pick" : dropped + " picks") + "."));
    }

    private static void reveal(Room room, Card card, Team active) {
        Team rival = active.other();
        card.reveal();
        room.getPlayers().forEach(player -> player.setHighlights(player.getHighlights().stream().filter(id -> !id.equals(card.getCardId())).toList()));
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

    private static void publishClue(Room room, ActiveClue clue) {
        room.setPhase(Phase.GUESSING);
        room.setClue(clue);
        Count number = clue.number();
        room.setGuessesRemaining(number.unlimited() || number.value() == 0 ? Count.UNLIMITED : Count.of(number.value() + 1));
        room.setTurnGuesses(0);
        room.setPendingReview(null);
        startClock(room, room.getOperativeTime());
        room.getClues().add(new Clue(room, room.getClues().size() + 1, clue));
    }

    private static void endTurn(Room room, boolean penaltyRevealPending) {
        room.setActiveTeam(room.getActiveTeam().other());
        room.setPhase(Phase.CLUE);
        room.setGuessesRemaining(Count.of(0));
        room.setTurnGuesses(0);
        room.setPendingReview(null);
        room.setPenaltyRevealPending(penaltyRevealPending);
        startClock(room, room.getSpymasterTime());
        clearHighlights(room);
    }

    /** Starts the clock for the turn's new phase, when the host has the timer on. */
    private static void startClock(Room room, Duration time) {
        room.setTurnEndsAt(room.isTurnTimer() ? Instant.now().plus(time) : null);
    }

    /** A turn length the host picked, or the current one when they left it out. */
    private static Duration turnTime(Integer seconds, Duration current) {
        if (seconds == null) return current;
        Duration time = Duration.ofSeconds(seconds);
        ensure(time.compareTo(MIN_TURN_TIME) >= 0 && time.compareTo(MAX_TURN_TIME) <= 0,
            "Pick a turn time from " + clock(MIN_TURN_TIME) + " to " + clock(MAX_TURN_TIME) + ".");
        return time;
    }

    /** 30 seconds, 1 minute, 1:30, 5 minutes. */
    static String clock(Duration time) {
        long minutes = time.toMinutes();
        int seconds = time.toSecondsPart();
        if (minutes == 0) return seconds + " seconds";
        if (seconds == 0) return minutes + (minutes == 1 ? " minute" : " minutes");
        return minutes + ":" + String.format("%02d", seconds);
    }

    private static void finish(Room room, Team winner, String message) {
        room.setPhase(Phase.FINISHED);
        room.setWinner(winner);
        room.setResultMessage(message);
        room.setPenaltyRevealPending(false);
        room.setPendingReview(null);
        room.setGuessesRemaining(Count.of(0));
        room.setTurnEndsAt(null);
        clearHighlights(room);
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
