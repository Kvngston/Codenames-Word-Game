package com.tk.wordagents.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameRulesTest {

    private Room room;
    private Team first;
    private Team second;

    @BeforeEach
    void setUp() {
        room = new Room("TEST1");
        GameRules.deal(room, new Random(7));
        room.setHostPlayerId("red-spy");
        addPlayer("red-spy", Team.RED, Seat.SPYMASTER);
        addPlayer("red-op", Team.RED, Seat.OPERATIVE);
        addPlayer("blue-spy", Team.BLUE, Seat.SPYMASTER);
        addPlayer("blue-op", Team.BLUE, Seat.OPERATIVE);
        first = room.getStartingTeam();
        second = first.other();
    }

    private void addPlayer(String id, Team team, Seat seat) {
        Player player = new Player(room, id, "hash-" + id, id, room.getPlayers().size());
        player.setTeam(team);
        player.setSeat(seat);
        room.getPlayers().add(player);
    }

    private String spy(Team team) { return team == Team.RED ? "red-spy" : "blue-spy"; }

    private String op(Team team) { return team == Team.RED ? "red-op" : "blue-op"; }

    private Card unrevealed(CardRole role) {
        return room.getCards().stream().filter(card -> card.getRole() == role && !card.isRevealed()).findFirst().orElseThrow();
    }

    private void start() {
        GameRules.apply(room, "red-spy", new GameAction.Start());
    }

    @Test
    void dealsNineEightSevenAndOne() {
        assertThat(room.getCards()).hasSize(25);
        assertThat(GameRules.total(room, first)).isEqualTo(9);
        assertThat(GameRules.total(room, second)).isEqualTo(8);
        assertThat(room.getCards()).filteredOn(card -> card.getRole() == CardRole.NEUTRAL).hasSize(7);
        assertThat(room.getCards()).filteredOn(card -> card.getRole() == CardRole.ASSASSIN).hasSize(1);
    }

    @Test
    void onlyTheHostStartsAndOnlyWhenSeated() {
        assertThatThrownBy(() -> GameRules.apply(room, "blue-op", new GameAction.Start())).hasMessageContaining("Only the host");
        room.getPlayers().get(3).setSeat(null);
        room.getPlayers().get(3).setTeam(null);
        assertThatThrownBy(this::start).hasMessageContaining("needs at least one operative");
    }

    @Test
    void oneSpymasterPerTeam() {
        assertThatThrownBy(() -> GameRules.apply(room, "red-op", new GameAction.TakeSeat(Team.RED, Seat.SPYMASTER)))
            .hasMessageContaining("already has a spymaster");
    }

    @Test
    void clueOpensGuessingWithNumberPlusOne() {
        start();
        assertThatThrownBy(() -> GameRules.apply(room, spy(second), new GameAction.GiveClue("ZEBRA", Count.of(2)))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> GameRules.apply(room, op(first), new GameAction.GiveClue("ZEBRA", Count.of(2)))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> GameRules.apply(room, spy(first), new GameAction.GiveClue("two words", Count.of(2)))).hasMessageContaining("one word");
        assertThatThrownBy(() -> GameRules.apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(12)))).hasMessageContaining("0 to 9");

        GameRules.apply(room, spy(first), new GameAction.GiveClue("zebra", Count.of(2)));
        assertThat(room.getPhase()).isEqualTo(Phase.GUESSING);
        assertThat(room.getGuessesRemaining()).isEqualTo(Count.of(3));
        assertThat(room.clue()).contains(new ActiveClue(first, "ZEBRA", Count.of(2)));
        assertThat(room.getClues()).hasSize(1);
    }

    @Test
    void zeroAndUnlimitedCluesHaveNoCap() {
        start();
        GameRules.apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(0)));
        assertThat(room.getGuessesRemaining().unlimited()).isTrue();
    }

    @Test
    void guessingFollowsTheBudgetAndPassesTheTurn() {
        start();
        GameRules.apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(1)));
        assertThatThrownBy(() -> GameRules.apply(room, op(first), new GameAction.EndTurn())).hasMessageContaining("at least one");
        assertThatThrownBy(() -> GameRules.apply(room, spy(first), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> GameRules.apply(room, op(second), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()))).isInstanceOf(GameException.class);

        GameRules.apply(room, op(first), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()));
        assertThat(room.getGuessesRemaining()).isEqualTo(Count.of(1));
        GameRules.apply(room, op(first), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()));
        assertThat(room.getActiveTeam()).isEqualTo(second);
        assertThat(room.getPhase()).isEqualTo(Phase.CLUE);
    }

    @Test
    void wrongGuessEndsTheTurn() {
        start();
        GameRules.apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(3)));
        GameRules.apply(room, op(first), new GameAction.Guess(unrevealed(CardRole.NEUTRAL).getCardId()));
        assertThat(room.getActiveTeam()).isEqualTo(second);
    }

    @Test
    void assassinHandsTheWinToTheOtherTeam() {
        start();
        GameRules.apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(1)));
        GameRules.apply(room, op(first), new GameAction.Guess(unrevealed(CardRole.ASSASSIN).getCardId()));
        assertThat(room.getPhase()).isEqualTo(Phase.FINISHED);
        assertThat(room.getWinner()).isEqualTo(second);
    }

    @Test
    void questionableClueGoesToTheOpposingSpymaster() {
        start();
        String boardWord = room.getCards().getFirst().getWord().split(" ")[0];
        GameRules.apply(room, spy(first), new GameAction.GiveClue(boardWord, Count.of(1)));
        assertThat(room.pendingReview()).isPresent();
        assertThat(room.getPhase()).isEqualTo(Phase.CLUE);
        assertThatThrownBy(() -> GameRules.apply(room, spy(first), new GameAction.ReviewClue(false))).hasMessageContaining("Only the");

        GameRules.apply(room, spy(second), new GameAction.ReviewClue(true));
        assertThat(room.getActiveTeam()).isEqualTo(second);
        assertThat(room.isPenaltyRevealPending()).isTrue();
        assertThatThrownBy(() -> GameRules.apply(room, spy(second), new GameAction.PenaltyReveal(unrevealed(first.cardRole()).getCardId())))
            .hasMessageContaining("must be a");
        Card bonus = unrevealed(second.cardRole());
        GameRules.apply(room, spy(second), new GameAction.PenaltyReveal(bonus.getCardId()));
        assertThat(bonus.isRevealed()).isTrue();
        assertThat(room.isPenaltyRevealPending()).isFalse();
    }

    @Test
    void newGameKeepsSeatsAndReturnsToLobby() {
        start();
        String oldId = room.getCards().getFirst().getCardId();
        GameRules.apply(room, "red-spy", new GameAction.NewGame());
        assertThat(room.getPhase()).isEqualTo(Phase.LOBBY);
        assertThat(room.getCards()).hasSize(25).noneMatch(Card::isRevealed);
        assertThat(room.getCards().getFirst().getCardId()).isNotEqualTo(oldId);
        assertThat(room.getPlayers()).allMatch(Player::isSeated);
    }

    @Test
    void seatsLockMidGameExceptLateOperatives() {
        start();
        assertThatThrownBy(() -> GameRules.apply(room, "red-op", new GameAction.TakeSeat(Team.BLUE, Seat.OPERATIVE))).hasMessageContaining("locked");
        Player late = new Player(room, "late", "hash-late", "late", 9);
        room.getPlayers().add(late);
        assertThatThrownBy(() -> GameRules.apply(room, "late", new GameAction.TakeSeat(Team.BLUE, Seat.SPYMASTER))).hasMessageContaining("locked");
        GameRules.apply(room, "late", new GameAction.TakeSeat(Team.BLUE, Seat.OPERATIVE));
        assertThat(late.getSeat()).isEqualTo(Seat.OPERATIVE);
    }
}
