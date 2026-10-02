package com.tk.wordagents.game;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A room and its current game. This is server-only state: it holds the secret
 * map, so clients only ever see the projection built by {@code RoomViews}.
 */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    private String code;

    @Column(name = "host_player_id", nullable = false)
    private String hostPlayerId;

    @Column(name = "red_name", nullable = false)
    private String redName = "Red";

    @Column(name = "blue_name", nullable = false)
    private String blueName = "Blue";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "starting_team", nullable = false)
    private Team startingTeam;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "active_team", nullable = false)
    private Team activeTeam;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private Phase phase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "clue_team")
    private Team clueTeam;

    @Column(name = "clue_word")
    private String clueWord;

    @Column(name = "clue_number")
    private Integer clueNumber;

    @Column(name = "review_word")
    private String reviewWord;

    @Column(name = "review_number")
    private Integer reviewNumber;

    @Column(name = "guesses_remaining")
    private Integer guessesRemaining;

    @Column(name = "turn_guesses", nullable = false)
    private int turnGuesses;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Team winner;

    @Column(name = "result_message", nullable = false)
    private String resultMessage = "";

    @Column(name = "last_event", nullable = false)
    private String lastEvent = "";

    @Column(name = "penalty_reveal_pending", nullable = false)
    private boolean penaltyRevealPending;

    /** Whether turns are timed. The host's choice; off unless they turn it on. */
    @Column(name = "turn_timer", nullable = false)
    private boolean turnTimer;

    /** How long each spymaster has for a clue, and each team's operatives to guess, with the timer on. */
    @Column(name = "spymaster_seconds", nullable = false)
    private int spymasterSeconds = (int) GameRules.DEFAULT_SPYMASTER_TIME.toSeconds();

    @Column(name = "operative_seconds", nullable = false)
    private int operativeSeconds = (int) GameRules.DEFAULT_OPERATIVE_TIME.toSeconds();

    /** When the active spymaster's or operatives' time runs out; null outside a turn or with the timer off. */
    @Column(name = "turn_ends_at")
    private Instant turnEndsAt;

    /** Comma-separated pack ids and saved-pack codes the board is dealt from. */
    @Column(name = "word_packs", nullable = false)
    private String wordPacks = WordPacks.DEFAULT_ID;

    /** The host's own words, one per line; they go on the board before pack words. */
    @Column(name = "custom_words", columnDefinition = "TEXT")
    private String customWords;

    /** How many distinct words the last deal drew from. */
    @Column(name = "pool_size", nullable = false)
    private int poolSize;

    /** Bumped on every change so clients can ignore out-of-order views. */
    @Column(nullable = false)
    private long revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinOrder")
    private List<Player> players = new ArrayList<>();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Card> cards = new ArrayList<>();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("turn")
    private List<Clue> clues = new ArrayList<>();

    protected Room() {}

    public Room(String code) {
        this.code = code;
        this.updatedAt = Instant.now();
    }

    public Optional<Player> player(String playerId) {
        return players.stream().filter(player -> player.getId().equals(playerId)).findFirst();
    }

    public String teamName(Team team) {
        return team == Team.RED ? redName : blueName;
    }

    public void setTeamName(Team team, String name) {
        if (team == Team.RED) redName = name;
        else blueName = name;
    }

    public Optional<ActiveClue> clue() {
        return clueWord == null ? Optional.empty() : Optional.of(new ActiveClue(clueTeam, clueWord, Count.fromColumn(clueNumber)));
    }

    public void setClue(ActiveClue clue) {
        clueTeam = clue == null ? null : clue.team();
        clueWord = clue == null ? null : clue.word();
        clueNumber = clue == null ? null : clue.number().value();
    }

    /** A clue waiting on the opposing spymaster's ruling; it always belongs to the active team. */
    public Optional<ActiveClue> pendingReview() {
        return reviewWord == null ? Optional.empty() : Optional.of(new ActiveClue(activeTeam, reviewWord, Count.fromColumn(reviewNumber)));
    }

    public void setPendingReview(ActiveClue clue) {
        reviewWord = clue == null ? null : clue.word();
        reviewNumber = clue == null ? null : clue.number().value();
    }

    public Count getGuessesRemaining() {
        return Count.fromColumn(guessesRemaining);
    }

    public void setGuessesRemaining(Count count) {
        guessesRemaining = count.value();
    }

    public List<String> getWordPacks() {
        return wordPacks == null || wordPacks.isBlank() ? List.of() : List.of(wordPacks.split(","));
    }

    public void setWordPacks(List<String> packs) {
        wordPacks = String.join(",", packs);
    }

    public List<String> getCustomWords() {
        return customWords == null || customWords.isBlank() ? List.of() : customWords.lines().toList();
    }

    public void setCustomWords(List<String> words) {
        customWords = words.isEmpty() ? null : String.join("\n", words);
    }

    public int getPoolSize() { return poolSize; }
    void setPoolSize(int poolSize) { this.poolSize = poolSize; }

    public void touch() {
        revision += 1;
        updatedAt = Instant.now();
    }

    public String getCode() { return code; }
    public String getHostPlayerId() { return hostPlayerId; }
    public void setHostPlayerId(String hostPlayerId) { this.hostPlayerId = hostPlayerId; }
    public Team getStartingTeam() { return startingTeam; }
    public void setStartingTeam(Team startingTeam) { this.startingTeam = startingTeam; }
    public Team getActiveTeam() { return activeTeam; }
    public void setActiveTeam(Team activeTeam) { this.activeTeam = activeTeam; }
    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }
    public int getTurnGuesses() { return turnGuesses; }
    public void setTurnGuesses(int turnGuesses) { this.turnGuesses = turnGuesses; }
    public Team getWinner() { return winner; }
    public void setWinner(Team winner) { this.winner = winner; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public String getLastEvent() { return lastEvent; }
    public void setLastEvent(String lastEvent) { this.lastEvent = lastEvent; }
    public boolean isPenaltyRevealPending() { return penaltyRevealPending; }
    public void setPenaltyRevealPending(boolean penaltyRevealPending) { this.penaltyRevealPending = penaltyRevealPending; }
    public boolean isTurnTimer() { return turnTimer; }
    public void setTurnTimer(boolean turnTimer) { this.turnTimer = turnTimer; }
    public Duration getSpymasterTime() { return Duration.ofSeconds(spymasterSeconds); }
    public void setSpymasterTime(Duration time) { spymasterSeconds = (int) time.toSeconds(); }
    public Duration getOperativeTime() { return Duration.ofSeconds(operativeSeconds); }
    public void setOperativeTime(Duration time) { operativeSeconds = (int) time.toSeconds(); }
    public Instant getTurnEndsAt() { return turnEndsAt; }
    public void setTurnEndsAt(Instant turnEndsAt) { this.turnEndsAt = turnEndsAt; }
    public long getRevision() { return revision; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Player> getPlayers() { return players; }
    public List<Card> getCards() { return cards; }
    public List<Clue> getClues() { return clues; }
}
