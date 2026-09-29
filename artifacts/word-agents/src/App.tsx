import { useEffect, useState, type CSSProperties, type FormEvent } from 'react';
import {
  ArrowRight,
  BookOpen,
  Check,
  Eye,
  EyeOff,
  Flag,
  KeyRound,
  RotateCcw,
  Shield,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import {
  createGame,
  getRemaining,
  loadSavedGame,
  otherTeam,
  type Card,
  type ClueNumber,
  type Game,
  type Role,
  type Team,
} from './game';

type Dialog = 'rules' | 'clue-check' | null;
type ToastMessage = string | null;

function endTurn(game: Game, penaltyRevealPending = false): Game {
  return {
    ...game,
    activeTeam: otherTeam(game.activeTeam),
    phase: 'handoff',
    guessesRemaining: 0,
    turnGuesses: 0,
    revealKey: false,
    penaltyRevealPending,
  };
}

function roleLabel(role: Role, teamNames: Record<Team, string>): string {
  if (role === 'red' || role === 'blue') return teamNames[role];
  return role === 'assassin' ? 'Assassin' : 'Neutral';
}

function App() {
  const [game, setGame] = useState<Game>(() => loadSavedGame() ?? createGame());
  const [dialog, setDialog] = useState<Dialog>(null);
  const [clueDraft, setClueDraft] = useState('');
  const [numberDraft, setNumberDraft] = useState<ClueNumber>(1);
  const [toast, setToast] = useState<ToastMessage>(null);

  useEffect(() => {
    try {
      localStorage.setItem('word-agents-game-v1', JSON.stringify(game));
    } catch {
      setToast('This browser could not save the game locally.');
    }
  }, [game]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), 2800);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const activeName = game.teamNames[game.activeTeam];
  const opposingTeam = otherTeam(game.activeTeam);
  const remainingRed = getRemaining(game, 'red');
  const remainingBlue = getRemaining(game, 'blue');
  const isBoardVisible = ['spymaster', 'guessing', 'finished'].includes(game.phase);
  const canRevealKey = game.phase === 'spymaster';

  function startGame() {
    setGame((current) => ({
      ...current,
      teamNames: {
        red: current.teamNames.red.trim() || 'Red',
        blue: current.teamNames.blue.trim() || 'Blue',
      },
      phase: 'handoff',
      activeTeam: current.startingTeam,
      revealKey: false,
      resultMessage: '',
    }));
  }

  function newGame() {
    setGame((current) => createGame(current.teamNames));
    setClueDraft('');
    setNumberDraft(1);
    setToast('A fresh word map is on the table.');
  }

  function updateTeamName(team: Team, value: string) {
    setGame((current) => ({
      ...current,
      teamNames: { ...current.teamNames, [team]: value },
    }));
  }

  function passToSpymaster() {
    setGame((current) => ({
      ...current,
      phase: 'spymaster',
      revealKey: false,
    }));
  }

  function publishClue(word = clueDraft, number = numberDraft) {
    const cleanWord = word.trim();
    const finite = number === 'unlimited' || number === 0 ? 'unlimited' : Number(number) + 1;
    setGame((current) => ({
      ...current,
      phase: 'guessing',
      clue: cleanWord.toUpperCase(),
      clueTeam: current.activeTeam,
      clueNumber: number,
      guessesRemaining: finite,
      revealKey: false,
      turnGuesses: 0,
      clueHistory: [
        ...current.clueHistory,
        { team: current.activeTeam, word: cleanWord.toUpperCase(), number, turn: current.clueHistory.length + 1 },
      ],
    }));
    setClueDraft('');
  }

  function isQuestionableClue(clue: string) {
    const parts = clue.trim().toLocaleLowerCase().split(/[\s-]+/).filter(Boolean);
    return game.cards.some((card) => {
      if (card.revealed) return false;
      const word = card.word.toLocaleLowerCase();
      return parts.some((part) => part === word || word.split(/[\s-]+/).includes(part));
    });
  }

  function submitClue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const clean = clueDraft.trim();
    if (!clean) {
      setToast('Enter a one-word clue first.');
      return;
    }
    if (/\s/.test(clean)) {
      setToast('Keep it to one word. A hyphenated word is okay.');
      return;
    }
    if (isQuestionableClue(clean)) {
      setDialog('clue-check');
      return;
    }
    publishClue(clean, numberDraft);
  }

  function adjudicateClue(isViolation: boolean) {
    setDialog(null);
    if (!isViolation) {
      publishClue(clueDraft, numberDraft);
      return;
    }
    setGame((current) => {
      const nextTeam = otherTeam(current.activeTeam);
      const hasFriendlyCard = current.cards.some((card) => card.role === nextTeam && !card.revealed);
      return {
        ...endTurn(current, hasFriendlyCard),
        resultMessage: '',
      };
    });
    setClueDraft('');
    setToast('Clue penalty accepted. The other team takes the handoff.');
  }

  function toggleKey() {
    if (!canRevealKey) return;
    setGame((current) => ({ ...current, revealKey: !current.revealKey }));
  }

  function finishGame(winner: Team, message: string, cards = game.cards) {
    setGame((current) => ({
      ...current,
      cards,
      phase: 'finished',
      winner,
      resultMessage: message,
      revealKey: false,
      penaltyRevealPending: false,
      guessesRemaining: 0,
    }));
  }

  function chooseCard(card: Card) {
    if (card.revealed) return;

    if (game.phase === 'spymaster' && game.penaltyRevealPending) {
      if (card.role !== game.activeTeam) {
        setToast(`The penalty reveal must be a ${activeName} card.`);
        return;
      }
      const cards = game.cards.map((item) => item.id === card.id ? { ...item, revealed: true } : item);
      if (getRemaining({ ...game, cards }, game.activeTeam) === 0) {
        finishGame(game.activeTeam, `${activeName} found the final friendly card on a clue penalty.`, cards);
      } else {
        setGame((current) => ({
          ...current,
          cards,
          penaltyRevealPending: false,
          revealKey: false,
        }));
        setToast(`${card.word} revealed for ${activeName}.`);
      }
      return;
    }

    if (game.phase !== 'guessing') return;
    const cards = game.cards.map((item) => item.id === card.id ? { ...item, revealed: true } : item);

    if (card.role === 'assassin') {
      finishGame(opposingTeam, `${activeName} uncovered the assassin.`, cards);
      return;
    }

    if (card.role === game.activeTeam) {
      const left = getRemaining({ ...game, cards }, game.activeTeam);
      if (left === 0) {
        finishGame(game.activeTeam, `${activeName} found every one of its agents.`, cards);
        return;
      }
      const nextBudget: Game['guessesRemaining'] = game.guessesRemaining === 'unlimited'
        ? 'unlimited'
        : Math.max(0, game.guessesRemaining - 1);
      const next: Game = { ...game, cards, turnGuesses: game.turnGuesses + 1, guessesRemaining: nextBudget };
      setGame(nextBudget === 0 ? endTurn(next) : next);
      return;
    }

    if (card.role === opposingTeam) {
      const opponentLeft = getRemaining({ ...game, cards }, opposingTeam);
      if (opponentLeft === 0) {
        finishGame(opposingTeam, `${game.teamNames[opposingTeam]} revealed its final agent.`, cards);
        return;
      }
      setGame(endTurn({ ...game, cards }));
      return;
    }

    setGame(endTurn({ ...game, cards }));
  }

  function stopTurn() {
    if (game.phase !== 'guessing' || game.turnGuesses < 1) {
      setToast('Make at least one correct guess before stopping.');
      return;
    }
    setGame(endTurn(game));
  }

  function skipPenaltyReveal() {
    setGame((current) => ({ ...current, penaltyRevealPending: false, revealKey: false }));
  }

  function renderCard(card: Card, index: number) {
    const showRole = canRevealKey && game.revealKey;
    const isBonusTarget = canRevealKey && game.revealKey && game.penaltyRevealPending && card.role === game.activeTeam && !card.revealed;
    const selectable = (game.phase === 'guessing' && !card.revealed) || isBonusTarget;
    const cardClass = [
      'word-card',
      card.revealed ? `revealed-${card.role}` : '',
      showRole && !card.revealed ? `key-${card.role}` : '',
    ].filter(Boolean).join(' ');
    const label = card.revealed
      ? `${card.word}, revealed as ${roleLabel(card.role, game.teamNames)}`
      : `${card.word}${showRole ? `, hidden role: ${roleLabel(card.role, game.teamNames)}` : ''}${isBonusTarget ? ', select for penalty reveal' : ''}`;

    return (
      <button
        key={card.id}
        type="button"
        className={cardClass}
        disabled={!selectable}
        onClick={() => chooseCard(card)}
        aria-label={label}
        data-testid={`card-word-${card.id}`}
        data-role={card.revealed ? card.role : undefined}
      >
        <span className="card-index">{String(index + 1).padStart(2, '0')}</span>
        <span>{card.word}</span>
      </button>
    );
  }

  function renderSetup() {
    return (
      <main className="layout">
        <section className="setup-wrap" aria-labelledby="welcome-title">
          <div className="setup-hero">
            <div>
              <div className="eyebrow">A game of whispers &amp; wild guesses</div>
              <h1 className="hero-title" id="welcome-title">Read the room.<br />Find your people.</h1>
              <p className="hero-copy">
                One clue. A crowded map. Two teams trying to think on the same wavelength.
                Gather close and keep the secret map to your side of the table.
              </p>
            </div>
            <div className="seal" aria-hidden="true"><div><b>25</b>words in play</div></div>
          </div>

          <section className="panel setup-panel" aria-label="Set up this game">
            <div className="section-heading">
              <h2>Set the table</h2>
              <p>Names are saved on this device.</p>
            </div>
            <div className="team-fields">
              <div className="field">
                <label htmlFor="red-name">Team one</label>
                <input
                  id="red-name"
                  value={game.teamNames.red}
                  onChange={(event) => updateTeamName('red', event.target.value)}
                  maxLength={18}
                  data-testid="input-team-red"
                />
              </div>
              <div className="field">
                <label htmlFor="blue-name">Team two</label>
                <input
                  id="blue-name"
                  value={game.teamNames.blue}
                  onChange={(event) => updateTeamName('blue', event.target.value)}
                  maxLength={18}
                  data-testid="input-team-blue"
                />
              </div>
            </div>
            <div className="setup-foot">
              <div className="privacy-note">
                <Shield size={17} aria-hidden="true" />
                <span>Pass one shared screen around. Only the spymaster sees the map; everyone else sees words, not roles.</span>
              </div>
              <button type="button" className="primary-button" onClick={startGame} data-testid="button-start-game">
                Deal the words <ArrowRight size={15} />
              </button>
            </div>
          </section>

          <div className="setup-details">
            <div className="detail-item"><KeyRound size={17} /><div><b>One map stays secret</b><span>Teams take turns passing the screen to their spymaster.</span></div></div>
            <div className="detail-item"><Users size={17} /><div><b>Talk it out together</b><span>Operatives read the clue, debate, then choose a word.</span></div></div>
            <div className="detail-item"><Sparkles size={17} /><div><b>Find every agent</b><span>Find your team first. Avoid the assassin at all costs.</span></div></div>
          </div>
        </section>
      </main>
    );
  }

  function renderHandoff() {
    const penaltyCopy = game.penaltyRevealPending
      ? `A clue was challenged. ${activeName}'s spymaster may reveal one friendly card before giving a clue.`
      : 'Pass the screen to your spymaster. Keep the map covered until they are ready.';
    return (
      <main className="layout">
        <ScoreStrip game={game} redLeft={remainingRed} blueLeft={remainingBlue} />
        {game.clue && <CurrentClue game={game} />}
        <section className="panel handoff-card" aria-labelledby="handoff-title">
          <div className="handoff-icon"><KeyRound size={28} /></div>
          <div className="eyebrow">Pass the screen · {game.clueHistory.length ? `Turn ${game.clueHistory.length + 1}` : 'First turn'}</div>
          <h1 className="handoff-title" id="handoff-title">Hand it to<br />{activeName}.</h1>
          <p className="handoff-copy">{penaltyCopy} Operatives, take a moment away from the screen.</p>
          <button type="button" className="primary-button" onClick={passToSpymaster} data-testid="button-pass-to-spymaster">
            I’m the spymaster <ArrowRight size={15} />
          </button>
          <div className="privacy-stamp"><Shield size={13} /> Shared-screen privacy handoff</div>
        </section>
        <HistoryPanel game={game} />
      </main>
    );
  }

  function renderSpymaster() {
    const canClue = !game.penaltyRevealPending;
    return (
      <main className="layout">
        <div className="game-layout">
          <section className="game-main" aria-label="Spymaster board">
            <GameHeading game={game} />
            <ScoreStrip game={game} redLeft={remainingRed} blueLeft={remainingBlue} />
            <div className="phase-banner">
              <KeyRound size={18} />
              <div><b>Spymaster view · {activeName}</b><span>Only your team should be looking at this screen.</span></div>
            </div>
            {game.penaltyRevealPending && (
              <div className="bonus-callout" data-testid="status-penalty-reveal">
                <strong>Clue penalty:</strong> reveal one unrevealed {activeName} card before giving your clue.
                <div className="action-row" style={{ marginTop: 9 }}>
                  {!game.revealKey && <button className="secondary-button" type="button" onClick={toggleKey} data-testid="button-show-penalty-map">Show the map</button>}
                  <button className="secondary-button" type="button" onClick={skipPenaltyReveal} data-testid="button-skip-penalty">Skip reveal</button>
                </div>
              </div>
            )}
            <div className="spymaster-tools">
              <span className="role-pill">Hidden key {game.revealKey ? 'exposed' : 'covered'}</span>
              <button type="button" className="quiet-button" onClick={toggleKey} aria-pressed={game.revealKey} data-testid="button-toggle-key">
                {game.revealKey ? <><EyeOff size={15} /> Hide map</> : <><Eye size={15} /> Reveal map</>}
              </button>
            </div>
            <div className="board" role="group" aria-label={game.revealKey ? 'Word board with secret roles' : 'Word board'}>
              {game.cards.map(renderCard)}
            </div>
            <div className="board-legend"><span>25 WORDS · {game.revealKey ? 'SECRET ROLES VISIBLE' : 'SECRET ROLES COVERED'}</span><span>Keep this side of the table clear</span></div>
          </section>
          <aside className="game-side">
            <section className="panel side-panel" aria-labelledby="clue-form-title">
              <h2 className="side-title" id="clue-form-title">{canClue ? 'Give a clue' : 'Reveal first'}</h2>
              {canClue ? (
                <>
                  <p className="turn-copy">Choose one word and a number. The number sets your target, plus one extra guess.</p>
                  <form className="clue-form" onSubmit={submitClue}>
                    <label className="sr-only" htmlFor="clue-input">One-word clue</label>
                    <input
                      id="clue-input"
                      value={clueDraft}
                      onChange={(event) => setClueDraft(event.target.value)}
                      placeholder="Your clue"
                      autoComplete="off"
                      maxLength={32}
                      data-testid="input-clue"
                    />
                    <label className="sr-only" htmlFor="clue-number">Guess number</label>
                    <select
                      id="clue-number"
                      value={numberDraft}
                      onChange={(event) => setNumberDraft(event.target.value === 'unlimited' ? 'unlimited' : Number(event.target.value))}
                      data-testid="select-clue-number"
                    >
                      <option value="0">0 · free</option>
                      {Array.from({ length: 9 }, (_, index) => <option key={index + 1} value={index + 1}>{index + 1}</option>)}
                      <option value="unlimited">∞ · unlimited</option>
                    </select>
                    <button type="submit" className="primary-button" data-testid="button-submit-clue">Give the clue <ArrowRight size={14} /></button>
                  </form>
                  <p className="turn-copy" style={{ marginTop: 11, marginBottom: 0 }}>A zero or unlimited clue has no numeric cap. Operatives must make one guess before stopping.</p>
                </>
              ) : (
                <p className="turn-copy">Use the hidden map above to choose one friendly card for the penalty reveal. Or skip it and give a clue.</p>
              )}
            </section>
            <HistoryPanel game={game} />
          </aside>
        </div>
      </main>
    );
  }

  function renderGuessingOrFinished() {
    const finished = game.phase === 'finished';
    return (
      <main className="layout">
        <div className="game-layout">
          <section className="game-main" aria-label="Operative word board">
            <GameHeading game={game} />
            <ScoreStrip game={game} redLeft={remainingRed} blueLeft={remainingBlue} />
            {finished && (
              <section className="result-panel" data-testid="status-game-result">
                <div className="eyebrow" style={{ color: '#e0c477' }}>Game over</div>
                <h2>{game.winner ? `${game.teamNames[game.winner]} takes the table.` : 'The map is closed.'}</h2>
                <p>{game.resultMessage}</p>
              </section>
            )}
            {game.phase === 'guessing' && (
              <div className="phase-banner">
                <Users size={18} />
                <div><b>Operatives · {activeName}</b><span>Talk it through. Choose a word, or stop after a correct guess.</span></div>
              </div>
            )}
            <div className="board" role="group" aria-label="Operative word board">
              {game.cards.map(renderCard)}
            </div>
            <div className="board-legend">
              <span>25 WORDS · {game.cards.filter((card) => card.revealed).length} REVEALED</span>
              <span>{finished ? 'Final board' : 'Choose carefully'}</span>
            </div>
          </section>
          <aside className="game-side">
            {finished ? (
              <section className="panel side-panel">
                <h2 className="side-title">Play again?</h2>
                <p className="turn-copy">A new game deals a fresh set of 25 words and a new hidden key.</p>
                <button type="button" className="primary-button" style={{ width: '100%' }} onClick={newGame} data-testid="button-new-game-finished">
                  New game <RotateCcw size={14} />
                </button>
              </section>
            ) : (
              <section className="panel side-panel" aria-labelledby="current-clue-title">
                <CurrentClue game={game} titleId="current-clue-title" />
                <div className="turn-copy" style={{ marginTop: 15 }}>
                  {game.guessesRemaining === 'unlimited'
                    ? 'No numeric cap. One correct guess is required before you can stop.'
                    : `${game.guessesRemaining} ${game.guessesRemaining === 1 ? 'guess' : 'guesses'} left in this clue budget.`}
                </div>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ width: '100%', marginTop: 4 }}
                  onClick={stopTurn}
                  disabled={game.turnGuesses < 1}
                  data-testid="button-stop-turn"
                >
                  End turn <Flag size={14} />
                </button>
                {game.turnGuesses < 1 && <p className="turn-copy" style={{ margin: '9px 0 0', fontSize: 10 }}>One guess minimum before you can end this turn.</p>}
              </section>
            )}
            <HistoryPanel game={game} />
          </aside>
        </div>
      </main>
    );
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-lockup" aria-label="Word Agents">
          <span className="brand-mark" aria-hidden="true" />
          <span><span className="brand-name">Word Agents</span><span className="brand-tag">Field notes for clever friends</span></span>
        </div>
        <div className="top-actions">
          {game.phase !== 'setup' && (
            <button type="button" className="quiet-button" onClick={newGame} data-testid="button-new-game">
              <RotateCcw size={14} /> New game
            </button>
          )}
          <button type="button" className="icon-button" aria-label="Read the rules" onClick={() => setDialog('rules')} data-testid="button-open-rules">
            <BookOpen size={17} />
          </button>
        </div>
      </header>

      {game.phase === 'setup' && renderSetup()}
      {game.phase === 'handoff' && renderHandoff()}
      {game.phase === 'spymaster' && renderSpymaster()}
      {(game.phase === 'guessing' || game.phase === 'finished') && renderGuessingOrFinished()}

      {isBoardVisible && (
        <div className="sr-only" aria-live="polite" data-testid="status-turn-and-counts">
          {activeName} to act. {game.teamNames.red}: {remainingRed} remaining. {game.teamNames.blue}: {remainingBlue} remaining.
        </div>
      )}

      {dialog && (
        <div className="overlay" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setDialog(null);
        }}>
          <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
            <div className="dialog-head">
              <div>
                <div className="eyebrow">{dialog === 'rules' ? 'Before the first clue' : 'Table decision'}</div>
                <h2 id="dialog-title">{dialog === 'rules' ? 'How to play' : 'Check this clue'}</h2>
              </div>
              <button type="button" className="icon-button" aria-label="Close dialog" onClick={() => setDialog(null)} data-testid="button-close-dialog"><X size={17} /></button>
            </div>
            {dialog === 'rules' ? (
              <>
                <p className="dialog-copy">Two teams. One spymaster at a time. The whole table shares one screen, so pass it carefully.</p>
                <ul className="rule-list">
                  <li>The spymaster gives a single-word clue and a number. The number means that many targets, plus one extra guess.</li>
                  <li>Operatives must guess once. A friendly agent keeps the turn going; a neutral or rival card passes the turn.</li>
                  <li>A zero or unlimited clue has no numeric cap. Stop after any correct guess if you have already guessed once.</li>
                  <li>Find every friendly agent to win. The assassin ends the game immediately for the other team.</li>
                  <li>Agree on clue restrictions before play. If a clue seems to break them, the opposing spymaster decides whether to accept a penalty.</li>
                </ul>
                <p className="dialog-copy">This is a pass-and-play game. Do not let operatives peek at a spymaster's screen.</p>
                <div className="dialog-actions"><button type="button" className="primary-button" onClick={() => setDialog(null)} data-testid="button-rules-got-it">Got it</button></div>
              </>
            ) : (
              <>
                <p className="dialog-copy">
                  This clue matches a hidden word or part of a compound word. Meaning and house rules are for your table to judge; this check is only a prompt, not an automatic ruling.
                </p>
                <div className="word-check" data-testid="text-questionable-clue">“{clueDraft.trim()}” · {game.teamNames[opposingTeam]} spymaster decides</div>
                <div className="dialog-actions">
                  <button type="button" className="secondary-button" onClick={() => adjudicateClue(false)} data-testid="button-accept-clue"><Check size={14} /> Accept clue</button>
                  <button type="button" className="danger-button" onClick={() => adjudicateClue(true)} data-testid="button-flag-clue"><Flag size={14} /> Uphold penalty</button>
                </div>
              </>
            )}
          </section>
        </div>
      )}
      {toast && <div className="toast-note" role="status" data-testid="status-toast">{toast}</div>}
    </div>
  );
}

function GameHeading({ game }: { game: Game }) {
  const team = game.activeTeam;
  const isSpy = game.phase === 'spymaster';
  return (
    <div className="game-head">
      <div>
        <div className="game-kicker"><span className="live-dot" /> Table is live · Pass &amp; play</div>
        <h1 className="game-title">{isSpy ? `${game.teamNames[team]} spymaster` : game.phase === 'finished' ? 'The final map' : 'The word board'}</h1>
      </div>
      <div className="turn-badge" style={{ '--team-color': team === 'red' ? 'var(--red)' : 'var(--blue)' } as CSSProperties}>
        <span className="team-dot" /> {game.phase === 'finished' ? 'Game finished' : `${game.teamNames[team]} turn`}
      </div>
    </div>
  );
}

function ScoreStrip({ game, redLeft, blueLeft }: { game: Game; redLeft: number; blueLeft: number }) {
  const redTotal = game.cards.filter((card) => card.role === 'red').length;
  const blueTotal = game.cards.filter((card) => card.role === 'blue').length;
  const redClaimed = redTotal - redLeft;
  const blueClaimed = blueTotal - blueLeft;
  return (
    <section className="score-strip" aria-label="Remaining agents">
      <div className="score-team" style={{ '--team-color': 'var(--red)' } as CSSProperties}>
        <span className="team-dot" />
        <div className="score-block"><div className="score-name" data-testid="text-team-name-red">{game.teamNames.red}</div><span className="score-sub">{redClaimed} found</span></div>
        <span className="score-number" data-testid="count-team-red">{redLeft}</span>
      </div>
      <span className="versus">REMAIN</span>
      <div className="score-team" style={{ '--team-color': 'var(--blue)' } as CSSProperties}>
        <span className="score-number" data-testid="count-team-blue">{blueLeft}</span>
        <div className="score-block"><div className="score-name" data-testid="text-team-name-blue">{game.teamNames.blue}</div><span className="score-sub">{blueClaimed} found</span></div>
        <span className="team-dot" />
      </div>
    </section>
  );
}

function CurrentClue({ game, titleId }: { game: Game; titleId?: string }) {
  const numberText = game.clueNumber === 'unlimited' ? '∞' : game.clueNumber;
  return (
    <div className="clue-entry" data-testid="panel-current-clue">
      <div className="clue-label" id={titleId}>Current clue · {game.teamNames[game.clueTeam ?? game.activeTeam]}</div>
      <div className="clue-word" data-testid="text-current-clue">{game.clue || 'Waiting for a clue'} <span className="clue-number" data-testid="text-clue-number">{numberText}</span></div>
      {game.guessesRemaining !== 0 && (
        <div className="guess-track" aria-label={`${game.guessesRemaining} guesses remaining`}>
          {Array.from({ length: 10 }, (_, index) => {
            const finite = game.guessesRemaining === 'unlimited' ? index < Math.max(game.turnGuesses + 1, 3) : index < game.guessesRemaining;
            return <span key={index} className={`guess-pip ${finite ? 'open' : 'used'}`} />;
          })}
          {game.guessesRemaining === 'unlimited' && <span className="clue-label">open-ended</span>}
        </div>
      )}
    </div>
  );
}

function HistoryPanel({ game }: { game: Game }) {
  return (
    <section className="panel side-panel history-panel" aria-labelledby="history-title">
      <div className="section-heading" style={{ marginBottom: 12 }}>
        <h2 className="side-title" id="history-title" style={{ margin: 0 }}>Clue trail</h2>
        <span className="role-pill" data-testid="text-clue-count">{game.clueHistory.length} {game.clueHistory.length === 1 ? 'clue' : 'clues'}</span>
      </div>
      {game.clueHistory.length ? (
        <div className="history-list" data-testid="list-clue-history">
          {[...game.clueHistory].reverse().map((record, index) => (
            <div className="history-row" key={`${record.turn}-${record.team}-${record.word}`} data-testid={`row-clue-${record.turn}`}>
              <span className="history-dot" style={{ '--team-color': record.team === 'red' ? 'var(--red)' : 'var(--blue)' } as CSSProperties} />
              <div><span className="history-clue">{record.word}</span><span className="history-meta">{game.teamNames[record.team]} · turn {record.turn}</span></div>
              <span className="history-count">{record.number === 'unlimited' ? '∞' : record.number}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-history" data-testid="empty-clue-history">No clues yet. The first spymaster is waiting to make a connection.</div>
      )}
    </section>
  );
}

export default App;