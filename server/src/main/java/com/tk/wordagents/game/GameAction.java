package com.tk.wordagents.game;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Everything a player can ask the server to do. The JSON {@code type} field picks the variant. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GameAction.TakeSeat.class, name = "take-seat"),
    @JsonSubTypes.Type(value = GameAction.LeaveSeat.class, name = "leave-seat"),
    @JsonSubTypes.Type(value = GameAction.RenameTeam.class, name = "rename-team"),
    @JsonSubTypes.Type(value = GameAction.Start.class, name = "start"),
    @JsonSubTypes.Type(value = GameAction.GiveClue.class, name = "give-clue"),
    @JsonSubTypes.Type(value = GameAction.ReviewClue.class, name = "review-clue"),
    @JsonSubTypes.Type(value = GameAction.PenaltyReveal.class, name = "penalty-reveal"),
    @JsonSubTypes.Type(value = GameAction.SkipPenalty.class, name = "skip-penalty"),
    @JsonSubTypes.Type(value = GameAction.Guess.class, name = "guess"),
    @JsonSubTypes.Type(value = GameAction.EndTurn.class, name = "end-turn"),
    @JsonSubTypes.Type(value = GameAction.NewGame.class, name = "new-game"),
})
public sealed interface GameAction {

    record TakeSeat(Team team, Seat seat) implements GameAction {}

    record LeaveSeat() implements GameAction {}

    record RenameTeam(Team team, String name) implements GameAction {}

    record Start() implements GameAction {}

    record GiveClue(String word, Count number) implements GameAction {}

    record ReviewClue(boolean uphold) implements GameAction {}

    record PenaltyReveal(String cardId) implements GameAction {}

    record SkipPenalty() implements GameAction {}

    record Guess(String cardId) implements GameAction {}

    record EndTurn() implements GameAction {}

    record NewGame() implements GameAction {}
}
