package com.tk.wordagents.game;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/** Everything a player can ask the server to do. The JSON {@code type} field picks the variant. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GameAction.TakeSeat.class, name = "take-seat"),
    @JsonSubTypes.Type(value = GameAction.LeaveSeat.class, name = "leave-seat"),
    @JsonSubTypes.Type(value = GameAction.RenameTeam.class, name = "rename-team"),
    @JsonSubTypes.Type(value = GameAction.SetWords.class, name = "set-words"),
    @JsonSubTypes.Type(value = GameAction.Start.class, name = "start"),
    @JsonSubTypes.Type(value = GameAction.GiveClue.class, name = "give-clue"),
    @JsonSubTypes.Type(value = GameAction.ReviewClue.class, name = "review-clue"),
    @JsonSubTypes.Type(value = GameAction.PenaltyReveal.class, name = "penalty-reveal"),
    @JsonSubTypes.Type(value = GameAction.SkipPenalty.class, name = "skip-penalty"),
    @JsonSubTypes.Type(value = GameAction.Guess.class, name = "guess"),
    @JsonSubTypes.Type(value = GameAction.Guesses.class, name = "guesses"),
    @JsonSubTypes.Type(value = GameAction.SetHighlights.class, name = "set-highlights"),
    @JsonSubTypes.Type(value = GameAction.EndTurn.class, name = "end-turn"),
    @JsonSubTypes.Type(value = GameAction.NewGame.class, name = "new-game"),
})
public sealed interface GameAction {

    record TakeSeat(Team team, Seat seat) implements GameAction {}

    record LeaveSeat() implements GameAction {}

    record RenameTeam(Team team, String name) implements GameAction {}

    /** Pack ids or saved-pack codes, plus the host's own words. Re-deals the lobby board. */
    record SetWords(List<String> packs, List<String> customWords) implements GameAction {}

    record Start() implements GameAction {}

    record GiveClue(String word, Count number) implements GameAction {}

    record ReviewClue(boolean uphold) implements GameAction {}

    record PenaltyReveal(String cardId) implements GameAction {}

    record SkipPenalty() implements GameAction {}

    record Guess(String cardId) implements GameAction {}

    /** Several picks at once, revealed in this order until the turn ends. */
    record Guesses(List<String> cardIds) implements GameAction {}

    /** Replaces the operative's highlighted words (thinking out loud, not a guess), in the order tapped. */
    record SetHighlights(List<String> cardIds) implements GameAction {}

    record EndTurn() implements GameAction {}

    record NewGame() implements GameAction {}
}
