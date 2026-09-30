package com.tk.wordagents.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameRulesTest {

    private Room room;
    private Team first;
    private Team second;

    @BeforeEach
    void setUp() {
        room = new Room("TEST1");
        GameRules.deal(room, WordLibrary.BUILT_IN, new Random(7));
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

    private static void apply(Room room, String playerId, GameAction action) {
        GameRules.apply(room, playerId, action, WordLibrary.BUILT_IN);
    }

    private static List<String> numbered(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> prefix + i).toList();
    }

    private List<String> boardWords() {
        return room.getCards().stream().map(Card::getWord).toList();
    }

    private String spy(Team team) { return team == Team.RED ? "red-spy" : "blue-spy"; }

    private String op(Team team) { return team == Team.RED ? "red-op" : "blue-op"; }

    private Card unrevealed(CardRole role) {
        return room.getCards().stream().filter(card -> card.getRole() == role && !card.isRevealed()).findFirst().orElseThrow();
    }

    private void start() {
        apply(room, "red-spy", new GameAction.Start());
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
        assertThatThrownBy(() -> apply(room, "blue-op", new GameAction.Start())).hasMessageContaining("Only the host");
        room.getPlayers().get(3).setSeat(null);
        room.getPlayers().get(3).setTeam(null);
        assertThatThrownBy(this::start).hasMessageContaining("needs at least one operative");
    }

    @Test
    void oneSpymasterPerTeam() {
        assertThatThrownBy(() -> apply(room, "red-op", new GameAction.TakeSeat(Team.RED, Seat.SPYMASTER)))
            .hasMessageContaining("already has a spymaster");
    }

    @Test
    void clueOpensGuessingWithNumberPlusOne() {
        start();
        assertThatThrownBy(() -> apply(room, spy(second), new GameAction.GiveClue("ZEBRA", Count.of(2)))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> apply(room, op(first), new GameAction.GiveClue("ZEBRA", Count.of(2)))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.GiveClue("two words", Count.of(2)))).hasMessageContaining("one word");
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(12)))).hasMessageContaining("0 to 9");

        apply(room, spy(first), new GameAction.GiveClue("zebra", Count.of(2)));
        assertThat(room.getPhase()).isEqualTo(Phase.GUESSING);
        assertThat(room.getGuessesRemaining()).isEqualTo(Count.of(3));
        assertThat(room.clue()).contains(new ActiveClue(first, "ZEBRA", Count.of(2)));
        assertThat(room.getClues()).hasSize(1);
    }

    @Test
    void zeroAndUnlimitedCluesHaveNoCap() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(0)));
        assertThat(room.getGuessesRemaining().unlimited()).isTrue();
    }

    @Test
    void guessingFollowsTheBudgetAndPassesTheTurn() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(1)));
        assertThatThrownBy(() -> apply(room, op(first), new GameAction.EndTurn())).hasMessageContaining("at least one");
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> apply(room, op(second), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()))).isInstanceOf(GameException.class);

        apply(room, op(first), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()));
        assertThat(room.getGuessesRemaining()).isEqualTo(Count.of(1));
        apply(room, op(first), new GameAction.Guess(unrevealed(first.cardRole()).getCardId()));
        assertThat(room.getActiveTeam()).isEqualTo(second);
        assertThat(room.getPhase()).isEqualTo(Phase.CLUE);
    }

    @Test
    void wrongGuessEndsTheTurn() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(3)));
        apply(room, op(first), new GameAction.Guess(unrevealed(CardRole.NEUTRAL).getCardId()));
        assertThat(room.getActiveTeam()).isEqualTo(second);
    }

    @Test
    void assassinHandsTheWinToTheOtherTeam() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(1)));
        apply(room, op(first), new GameAction.Guess(unrevealed(CardRole.ASSASSIN).getCardId()));
        assertThat(room.getPhase()).isEqualTo(Phase.FINISHED);
        assertThat(room.getWinner()).isEqualTo(second);
    }

    /** Re-deals with these words guaranteed on the board. The deal picks a new starting team. */
    private void dealWith(String... words) {
        apply(room, "red-spy", new GameAction.SetWords(List.of("classic"), List.of(words)));
        first = room.getStartingTeam();
        second = first.other();
    }

    private Card card(String word) {
        return room.getCards().stream().filter(card -> card.getWord().equals(word)).findFirst().orElseThrow();
    }

    @Test
    void cluesThatAreOnTheBoardAreRejected() {
        dealWith("SCUBA DIVER", "BONFIRE");
        start();
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.GiveClue("bonfire", Count.of(1)))).hasMessageContaining("“BONFIRE” is on the board");
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.GiveClue("SCUBA", Count.of(1)))).hasMessageContaining("part of “SCUBA DIVER”");
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.GiveClue("scuba-diver", Count.of(1)))).hasMessageContaining("is on the board");
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.GiveClue("SCUBADIVER", Count.of(1)))).hasMessageContaining("is on the board");
        assertThat(room.pendingReview()).isEmpty();
        assertThat(room.getPhase()).isEqualTo(Phase.CLUE);
        assertThat(room.getActiveTeam()).as("a rejected clue costs nothing").isEqualTo(first);
    }

    @Test
    void revealedWordsCanBeUsedAsClues() {
        dealWith("BONFIRE");
        start();
        card("BONFIRE").reveal();
        apply(room, spy(first), new GameAction.GiveClue("BONFIRE", Count.of(1)));
        assertThat(room.clue()).map(ActiveClue::word).contains("BONFIRE");
    }

    @Test
    void nearMatchesGoToTheOpposingSpymaster() {
        dealWith("BONFIRE");
        start();
        apply(room, spy(first), new GameAction.GiveClue("BONFIRES", Count.of(1)));
        assertThat(room.pendingReview()).isPresent();
        assertThat(room.getPhase()).isEqualTo(Phase.CLUE);
        assertThatThrownBy(() -> apply(room, spy(first), new GameAction.ReviewClue(false))).hasMessageContaining("Only the");

        apply(room, spy(second), new GameAction.ReviewClue(true));
        assertThat(room.getActiveTeam()).isEqualTo(second);
        assertThat(room.isPenaltyRevealPending()).isTrue();
        assertThatThrownBy(() -> apply(room, spy(second), new GameAction.PenaltyReveal(unrevealed(first.cardRole()).getCardId())))
            .hasMessageContaining("must be a");
        Card bonus = unrevealed(second.cardRole());
        apply(room, spy(second), new GameAction.PenaltyReveal(bonus.getCardId()));
        assertThat(bonus.isRevealed()).isTrue();
        assertThat(room.isPenaltyRevealPending()).isFalse();
    }

    private List<String> ids(Card... cards) {
        return java.util.Arrays.stream(cards).map(Card::getCardId).toList();
    }

    private List<Card> all(CardRole role) {
        return room.getCards().stream().filter(card -> card.getRole() == role && !card.isRevealed()).toList();
    }

    @Test
    void operativesCanSubmitSeveralPicksInTheOrderChosen() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(2)));
        Card one = all(first.cardRole()).get(0);
        Card two = all(first.cardRole()).get(1);
        Card neutral = unrevealed(CardRole.NEUTRAL);
        apply(room, op(first), new GameAction.Guesses(ids(one, two, neutral)));
        assertThat(List.of(one, two, neutral)).allMatch(Card::isRevealed);
        assertThat(room.getActiveTeam()).isEqualTo(second);
        assertThat(room.getLastEvent()).startsWith(op(first) + " picked " + one.getWord()).contains(two.getWord(), neutral.getWord());
    }

    @Test
    void aWrongPickEndsTheTurnAndLeavesLaterPicksHidden() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(2)));
        Card rival = unrevealed(second.cardRole());
        Card friendly = unrevealed(first.cardRole());
        apply(room, op(first), new GameAction.Guesses(ids(rival, friendly)));
        assertThat(rival.isRevealed()).isTrue();
        assertThat(friendly.isRevealed()).isFalse();
        assertThat(room.getActiveTeam()).isEqualTo(second);
        assertThat(room.getLastEvent()).contains("The turn ended before the other pick.");
    }

    @Test
    void theAssassinStopsABatch() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.UNLIMITED));
        Card friendly = unrevealed(first.cardRole());
        apply(room, op(first), new GameAction.Guesses(ids(unrevealed(CardRole.ASSASSIN), friendly)));
        assertThat(room.getPhase()).isEqualTo(Phase.FINISHED);
        assertThat(room.getWinner()).isEqualTo(second);
        assertThat(friendly.isRevealed()).isFalse();
    }

    @Test
    void picksMustFitTheClueBudgetAndBeDistinct() {
        start();
        apply(room, spy(first), new GameAction.GiveClue("ZEBRA", Count.of(1)));
        List<Card> friendly = all(first.cardRole());
        assertThatThrownBy(() -> apply(room, op(first), new GameAction.Guesses(ids(friendly.get(0), friendly.get(1), friendly.get(2)))))
            .hasMessageContaining("2 guesses left");
        assertThatThrownBy(() -> apply(room, op(first), new GameAction.Guesses(ids(friendly.get(0), friendly.get(0))))).hasMessageContaining("only be picked once");
        assertThatThrownBy(() -> apply(room, op(first), new GameAction.Guesses(List.of()))).hasMessageContaining("at least one");
        assertThatThrownBy(() -> apply(room, op(second), new GameAction.Guesses(ids(friendly.get(0))))).hasMessageContaining("operatives can guess");
        assertThat(friendly).noneMatch(Card::isRevealed);
    }

    @Test
    void newGameKeepsSeatsAndReturnsToLobby() {
        start();
        String oldId = room.getCards().getFirst().getCardId();
        apply(room, "red-spy", new GameAction.NewGame());
        assertThat(room.getPhase()).isEqualTo(Phase.LOBBY);
        assertThat(room.getCards()).hasSize(25).noneMatch(Card::isRevealed);
        assertThat(room.getCards().getFirst().getCardId()).isNotEqualTo(oldId);
        assertThat(room.getPlayers()).allMatch(Player::isSeated);
    }

    @Test
    void seatsLockMidGameExceptLateOperatives() {
        start();
        assertThatThrownBy(() -> apply(room, "red-op", new GameAction.TakeSeat(Team.BLUE, Seat.OPERATIVE))).hasMessageContaining("locked");
        Player late = new Player(room, "late", "hash-late", "late", 9);
        room.getPlayers().add(late);
        assertThatThrownBy(() -> apply(room, "late", new GameAction.TakeSeat(Team.BLUE, Seat.SPYMASTER))).hasMessageContaining("locked");
        apply(room, "late", new GameAction.TakeSeat(Team.BLUE, Seat.OPERATIVE));
        assertThat(late.getSeat()).isEqualTo(Seat.OPERATIVE);
    }

    @Test
    void builtInPacksCanEachFillABoard() {
        assertThat(WordPacks.all()).extracting(WordPacks.Pack::id).doesNotHaveDuplicates().contains(WordPacks.DEFAULT_ID);
        assertThat(WordPacks.all()).allSatisfy(pack -> assertThat(pack.words()).hasSizeGreaterThanOrEqualTo(GameRules.BOARD_SIZE).doesNotHaveDuplicates());
    }

    @Test
    void roomsDealFromTheClassicPackByDefault() {
        assertThat(room.getWordPacks()).containsExactly(WordPacks.DEFAULT_ID);
        assertThat(WordPacks.defaultPack().words()).containsAll(boardWords());
        assertThat(boardWords()).doesNotHaveDuplicates();
        assertThat(room.getPoolSize()).isEqualTo(WordPacks.defaultPack().words().size());
    }

    @Test
    void customWordsAlwaysMakeTheBoardAndPacksFillTheRest() {
        List<String> custom = numbered("AGENT ", 10);
        apply(room, "red-spy", new GameAction.SetWords(List.of("Food"), custom));
        assertThat(room.getWordPacks()).containsExactly("food");
        assertThat(room.getCustomWords()).isEqualTo(custom);
        assertThat(boardWords()).hasSize(25).containsAll(custom).doesNotHaveDuplicates();
        assertThat(boardWords()).filteredOn(word -> !custom.contains(word)).allMatch(WordPacks.find("food").orElseThrow().words()::contains);
        assertThat(room.getPoolSize()).isEqualTo(10 + WordPacks.find("food").orElseThrow().words().size());
        assertThat(room.getPhase()).isEqualTo(Phase.LOBBY);
    }

    @Test
    void customWordsAloneMustFillTheBoard() {
        assertThatThrownBy(() -> apply(room, "red-spy", new GameAction.SetWords(List.of(), numbered("W", 10))))
            .hasMessageContaining("only 10 words. Add 15 more");
        assertThatThrownBy(() -> apply(room, "red-spy", new GameAction.SetWords(List.of(), List.of()))).hasMessageContaining("Pick a pack");
        assertThat(room.getWordPacks()).containsExactly(WordPacks.DEFAULT_ID);

        apply(room, "red-spy", new GameAction.SetWords(List.of(), numbered("W", 25)));
        assertThat(boardWords()).containsExactlyInAnyOrderElementsOf(numbered("W", 25));
    }

    @Test
    void customWordsAreCleanedAndChecked() {
        List<String> messy = new java.util.ArrayList<>(List.of("  scuba   diver ", "SCUBA DIVER", "", "rock'n-roll"));
        messy.addAll(numbered("w", 23));
        apply(room, "red-spy", new GameAction.SetWords(List.of(), messy));
        assertThat(room.getCustomWords()).startsWith("SCUBA DIVER", "ROCK'N-ROLL").hasSize(25);
        assertThatThrownBy(() -> apply(room, "red-spy", new GameAction.SetWords(List.of("classic"), List.of("<b>bold</b>")))).hasMessageContaining("can't go on a card");
        assertThatThrownBy(() -> apply(room, "red-spy", new GameAction.SetWords(List.of("classic"), List.of("A".repeat(21))))).hasMessageContaining("too long");
    }

    @Test
    void onlyTheHostSetsWordsAndOnlyInTheLobby() {
        assertThatThrownBy(() -> apply(room, "blue-op", new GameAction.SetWords(List.of("food"), List.of()))).hasMessageContaining("Only the host");
        start();
        assertThatThrownBy(() -> apply(room, "red-spy", new GameAction.SetWords(List.of("food"), List.of()))).hasMessageContaining("locked");
    }

    @Test
    void savedPacksResolveByCodeAndUnknownPacksAreRejected() {
        List<String> saved = numbered("SAVED ", 30);
        WordLibrary library = id -> id.equals("ABC234") ? Optional.of(saved) : WordLibrary.BUILT_IN.words(id);
        assertThatThrownBy(() -> GameRules.apply(room, "red-spy", new GameAction.SetWords(List.of("NOPE99"), List.of()), library))
            .hasMessageContaining("no word pack with the code NOPE99");

        GameRules.apply(room, "red-spy", new GameAction.SetWords(List.of("abc234", "ABC234"), List.of()), library);
        assertThat(room.getWordPacks()).containsExactly("ABC234");
        assertThat(saved).containsAll(boardWords());

        // The pack is deleted before the next game: the deal falls back to Classic rather than failing.
        start();
        apply(room, "red-spy", new GameAction.NewGame());
        assertThat(boardWords()).hasSize(25).allMatch(WordPacks.defaultPack().words()::contains);
    }
}
