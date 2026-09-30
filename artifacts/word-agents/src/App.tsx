import { useEffect, useState, type CSSProperties, type FormEvent } from 'react';
import {
  ArrowRight,
  BookOpen,
  Check,
  Copy,
  Crown,
  Eye,
  EyeOff,
  Flag,
  KeyRound,
  Library,
  LogOut,
  RotateCcw,
  Shield,
  Sparkles,
  Users,
  WifiOff,
  X,
} from 'lucide-react';
import {
  otherTeam,
  seatingProblem,
  type Action,
  type CardRole,
  type CardView,
  type ClueNumber,
  type PlayerView,
  type RoomView,
  type Team,
} from '@workspace/game-core';
import { createRoom, forgetSeat, joinRoom, leaveRoom, loadSeat, sendAction, useRoom, type Connection, type Seat } from './room-client';
import { PacksDialog, WordsPanel } from './word-packs';

type ToastMessage = string | null;
type Act = (action: Action) => Promise<boolean>;

const BASE = import.meta.env.BASE_URL.replace(/\/$/, '');

function codeFromLocation(): string | null {
  const match = window.location.pathname.slice(BASE.length).match(/^\/room\/([A-Za-z0-9]{4,8})\/?$/);
  return match ? match[1].toUpperCase() : null;
}

/** A shared pack link: /pack/CODE opens the pack so it can be saved. */
function packFromLocation(): string | null {
  const match = window.location.pathname.slice(BASE.length).match(/^\/pack\/([A-Za-z0-9]{4,8})\/?$/);
  return match ? match[1].toUpperCase() : null;
}

function navigate(code: string | null) {
  window.history.pushState(null, '', code ? `${BASE}/room/${code}` : `${BASE}/`);
}

function roomLink(code: string) {
  return `${window.location.origin}${BASE}/room/${code}`;
}

function roleLabel(role: CardRole, teamNames: Record<Team, string>): string {
  if (role === 'red' || role === 'blue') return teamNames[role];
  return role === 'assassin' ? 'Assassin' : 'Neutral';
}

function seatLabel(player: PlayerView, teamNames: Record<Team, string>): string {
  if (!player.team || !player.seat) return 'Spectator';
  return `${teamNames[player.team]} ${player.seat}`;
}

function teamStyle(team: Team): CSSProperties {
  return { '--team-color': team === 'red' ? 'var(--red)' : 'var(--blue)' } as CSSProperties;
}

function App() {
  const [code, setCode] = useState<string | null>(codeFromLocation);
  const [seat, setSeat] = useState<Seat | null>(() => (code ? loadSeat(code) : null));
  const [toast, setToast] = useState<ToastMessage>(null);
  const [rulesOpen, setRulesOpen] = useState(false);
  const [sharedPack] = useState(packFromLocation);
  const [packsOpen, setPacksOpen] = useState(sharedPack !== null);
  const { view, connection, accept } = useRoom(seat);

  useEffect(() => {
    const onPop = () => {
      const next = codeFromLocation();
      setCode(next);
      setSeat(next ? loadSeat(next) : null);
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), 2800);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (view?.lastEvent) setToast(view.lastEvent);
  }, [view?.lastEvent]);

  function enterRoom(next: Seat) {
    navigate(next.code);
    setCode(next.code);
    setSeat(next);
  }

  function goHome() {
    navigate(null);
    setCode(null);
    setSeat(null);
  }

  const act: Act = async (action) => {
    if (!seat) return false;
    try {
      accept(await sendAction(seat, action));
      return true;
    } catch (error) {
      setToast((error as Error).message);
      return false;
    }
  };

  async function leave() {
    if (!seat) return;
    try {
      await leaveRoom(seat);
      goHome();
    } catch (error) {
      setToast((error as Error).message);
    }
  }

  function closePacks() {
    setPacksOpen(false);
    // Leave the shared pack link once it has been looked at.
    if (packFromLocation()) window.history.replaceState(null, '', `${BASE}/`);
  }

  function abandonSeat() {
    if (code) forgetSeat(code);
    goHome();
  }

  const isHost = view?.you.isHost ?? false;

  return (
    <div className="app-shell">
      <div className="classification-bar" aria-hidden="true"><span>Top secret</span><span>//</span><span>Eyes only</span></div>
      <header className="topbar">
        <div className="brand-lockup" aria-label="Word Agents">
          <span className="brand-mark" aria-hidden="true" />
          <span><span className="brand-name">Word Agents</span><span className="brand-tag">Covert word operations</span></span>
        </div>
        <div className="top-actions">
          {view && <span className="role-pill room-pill" data-testid="text-room-code">Op {view.code}</span>}
          {view && isHost && view.phase !== 'lobby' && (
            <button type="button" className="quiet-button" onClick={() => act({ type: 'new-game' })} data-testid="button-new-game">
              <RotateCcw size={14} /> New game
            </button>
          )}
          {view && (view.phase === 'lobby' || !view.you.seat) && (
            <button type="button" className="icon-button" aria-label="Leave room" onClick={leave} data-testid="button-leave-room">
              <LogOut size={17} />
            </button>
          )}
          <button type="button" className="icon-button" aria-label="Word packs" onClick={() => setPacksOpen(true)} data-testid="button-open-packs">
            <Library size={17} />
          </button>
          <button type="button" className="icon-button" aria-label="Read the rules" onClick={() => setRulesOpen(true)} data-testid="button-open-rules">
            <BookOpen size={17} />
          </button>
        </div>
      </header>

      {!seat && <Home initialCode={code} onSeat={enterRoom} onError={setToast} />}
      {seat && connection === 'lost' && <LostRoom onHome={abandonSeat} />}
      {seat && connection !== 'lost' && !view && <main className="layout"><div className="panel handoff-card"><p className="handoff-copy loading-copy">Establishing secure line</p></div></main>}
      {seat && connection !== 'lost' && view && (
        <>
          {connection === 'reconnecting' && <ConnectionBanner connection={connection} />}
          {view.phase === 'lobby' ? <Lobby view={view} act={act} onToast={setToast} onManagePacks={() => setPacksOpen(true)} /> : <Table view={view} act={act} />}
          <div className="sr-only" aria-live="polite" data-testid="status-turn-and-counts">
            {view.teamNames[view.activeTeam]} to act. {view.teamNames.red}: {view.remaining.red} remaining. {view.teamNames.blue}: {view.remaining.blue} remaining.
          </div>
        </>
      )}

      {rulesOpen && <RulesDialog onClose={() => setRulesOpen(false)} />}
      {packsOpen && <PacksDialog initialCode={sharedPack} onClose={closePacks} onToast={setToast} />}
      {view && <ReviewDialog view={view} act={act} />}
      {toast && <div className="toast-note" role="status" data-testid="status-toast">{toast}</div>}
    </div>
  );
}

function Home({ initialCode, onSeat, onError }: { initialCode: string | null; onSeat: (seat: Seat) => void; onError: (message: string) => void }) {
  const [name, setName] = useState(() => {
    try {
      return localStorage.getItem('word-agents-name') ?? '';
    } catch {
      return '';
    }
  });
  const [joinCode, setJoinCode] = useState(initialCode ?? '');
  const [busy, setBusy] = useState(false);

  async function run(task: () => Promise<Seat>) {
    if (!name.trim()) {
      onError('Enter your name first.');
      return;
    }
    try {
      localStorage.setItem('word-agents-name', name.trim());
    } catch {
      // Remembering the name is only a convenience.
    }
    setBusy(true);
    try {
      onSeat(await task());
    } catch (error) {
      onError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function submitJoin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!joinCode.trim()) {
      onError('Enter the room code from the host.');
      return;
    }
    void run(() => joinRoom(joinCode.trim(), name.trim()));
  }

  return (
    <main className="layout">
      <section className="setup-wrap" aria-labelledby="welcome-title">
        <div className="setup-hero">
          <div>
            <div className="eyebrow">Classified briefing · Eyes only</div>
            <h1 className="hero-title" id="welcome-title">Crack the code.<br /><em>Find your agents.</em></h1>
            <p className="hero-copy">
              One clue. Twenty-five cover names. Two rival networks trying to think on the same wavelength.
              Every agent plays on their own phone, and only spymasters ever receive the <span className="redact" tabIndex={0}>secret</span> map.
            </p>
          </div>
          <div className="seal" aria-hidden="true"><div><b>25</b>targets tracked</div></div>
        </div>

        <section className="panel setup-panel" aria-label="Join an operation">
          <div className="section-heading">
            <h2>{initialCode ? `Join operation ${initialCode}` : 'Report for duty'}</h2>
            <p>Your cover is remembered on this device.</p>
          </div>
          <div className="field">
            <label htmlFor="player-name">Codename</label>
            <input id="player-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={24} autoComplete="nickname" data-testid="input-player-name" />
          </div>
          <form className="join-row" onSubmit={submitJoin}>
            <div className="field">
              <label htmlFor="room-code">Access code</label>
              <input
                id="room-code"
                value={joinCode}
                onChange={(event) => setJoinCode(event.target.value.toUpperCase())}
                maxLength={8}
                autoComplete="off"
                placeholder="ABCDE"
                data-testid="input-room-code"
              />
            </div>
            <button type="submit" className="secondary-button" disabled={busy} data-testid="button-join-room">
              Join op <ArrowRight size={15} />
            </button>
          </form>
          {!initialCode && (
            <div className="setup-foot">
              <div className="privacy-note">
                <Shield size={17} aria-hidden="true" />
                <span>Running the mission? Open an operation, then send the access code or link to every agent in your crew.</span>
              </div>
              <button type="button" className="primary-button" disabled={busy} onClick={() => run(() => createRoom(name.trim()))} data-testid="button-create-room">
                Open an operation <ArrowRight size={15} />
              </button>
            </div>
          )}
        </section>

        <div className="setup-details">
          <div className="detail-item"><KeyRound size={17} /><div><b>Need to know</b><span>Each spymaster sees the map on their own screen. Nobody else’s device ever receives it.</span></div></div>
          <div className="detail-item"><Users size={17} /><div><b>Debrief together</b><span>Operatives decode the clue, argue it out, then tap a word on their phone.</span></div></div>
          <div className="detail-item"><Sparkles size={17} /><div><b>Extract your agents</b><span>Make contact with your whole team first. Avoid the assassin at all costs.</span></div></div>
        </div>
      </section>
    </main>
  );
}

function Lobby({ view, act, onToast, onManagePacks }: { view: RoomView; act: Act; onToast: (message: string) => void; onManagePacks: () => void }) {
  const [names, setNames] = useState(view.teamNames);
  useEffect(() => setNames(view.teamNames), [view.teamNames.red, view.teamNames.blue]);
  const problem = seatingProblem(view.players, view.teamNames);
  const unseated = view.players.filter((player) => !player.seat);

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(roomLink(view.code));
      onToast('Room link copied.');
    } catch {
      onToast(`Share this code: ${view.code}`);
    }
  }

  function commitName(team: Team) {
    if (names[team] !== view.teamNames[team]) void act({ type: 'rename-team', team, name: names[team] });
  }

  return (
    <main className="layout">
      <section className="setup-wrap">
        <div className="lobby-head">
          <div>
            <div className="eyebrow">Operation {view.code} · {view.players.length} {view.players.length === 1 ? 'agent' : 'agents'} on the line</div>
            <h1 className="handoff-title">Choose your post.</h1>
            <p className="handoff-copy lobby-copy">Each network needs one spymaster and at least one field operative. You are agent <b>{view.you.name}</b>{view.you.isHost ? ', the handler' : ''}.</p>
          </div>
          <div className="share-box">
            <span className="room-code-big" data-testid="text-room-code-big">{view.code}</span>
            <button type="button" className="quiet-button" onClick={copyLink} data-testid="button-copy-link"><Copy size={14} /> Copy link</button>
          </div>
        </div>

        <div className="team-columns">
          {(['red', 'blue'] as const).map((team) => {
            const spymaster = view.players.find((player) => player.team === team && player.seat === 'spymaster');
            const operatives = view.players.filter((player) => player.team === team && player.seat === 'operative');
            const youHere = view.you.team === team;
            return (
              <section key={team} className="panel team-column" style={teamStyle(team)} aria-label={`${view.teamNames[team]} team`}>
                <div className="team-column-head">
                  <span className="team-dot" />
                  {view.you.isHost ? (
                    <input
                      className="team-name-input"
                      value={names[team]}
                      maxLength={18}
                      onChange={(event) => setNames({ ...names, [team]: event.target.value })}
                      onBlur={() => commitName(team)}
                      onKeyDown={(event) => event.key === 'Enter' && event.currentTarget.blur()}
                      aria-label={`Rename team ${team}`}
                      data-testid={`input-team-${team}`}
                    />
                  ) : (
                    <h2 className="side-title" style={{ margin: 0 }} data-testid={`text-team-name-${team}`}>{view.teamNames[team]}</h2>
                  )}
                  {view.startingTeam === team && <span className="role-pill">First move</span>}
                </div>

                <div className="seat-label"><KeyRound size={13} /> Spymaster</div>
                {spymaster ? (
                  <PlayerRow player={spymaster} you={view.you} />
                ) : (
                  <button type="button" className="seat-button" onClick={() => act({ type: 'take-seat', team, seat: 'spymaster' })} data-testid={`button-seat-${team}-spymaster`}>
                    Take the codebook
                  </button>
                )}

                <div className="seat-label"><Users size={13} /> Operatives</div>
                {operatives.map((player) => <PlayerRow key={player.id} player={player} you={view.you} />)}
                {!(youHere && view.you.seat === 'operative') && (
                  <button type="button" className="seat-button" onClick={() => act({ type: 'take-seat', team, seat: 'operative' })} data-testid={`button-seat-${team}-operative`}>
                    Enlist as operative
                  </button>
                )}
              </section>
            );
          })}
        </div>

        {unseated.length > 0 && (
          <section className="panel side-panel waiting-panel">
            <div className="seat-label">Awaiting assignment</div>
            <div className="player-chips">{unseated.map((player) => <PlayerRow key={player.id} player={player} you={view.you} />)}</div>
          </section>
        )}

        <WordsPanel view={view} act={act} onToast={onToast} onManagePacks={onManagePacks} />

        <section className="panel setup-panel lobby-foot">
          <div className="privacy-note">
            <Shield size={17} aria-hidden="true" />
            <span>{problem ?? 'All posts filled. The spymasters receive the map the moment the words are dealt.'}</span>
          </div>
          <div className="action-row">
            {view.you.seat && (
              <button type="button" className="secondary-button" onClick={() => act({ type: 'leave-seat' })} data-testid="button-leave-seat">Stand down</button>
            )}
            {view.you.isHost ? (
              <button type="button" className="primary-button" disabled={Boolean(problem)} onClick={() => act({ type: 'start' })} data-testid="button-start-game">
                Launch mission <ArrowRight size={15} />
              </button>
            ) : (
              <span className="role-pill">Awaiting the handler’s go</span>
            )}
          </div>
        </section>
      </section>
    </main>
  );
}

function PlayerRow({ player, you }: { player: PlayerView; you: PlayerView }) {
  return (
    <div className={`player-row${player.connected ? '' : ' offline'}`} data-testid={`row-player-${player.id}`}>
      <span className={`presence-dot${player.connected ? ' online' : ''}`} aria-label={player.connected ? 'Online' : 'Offline'} />
      <span className="player-name">{player.name}{player.id === you.id && ' (you)'}</span>
      {player.isHost && <Crown size={13} aria-label="Handler" />}
    </div>
  );
}

function Table({ view, act }: { view: RoomView; act: Act }) {
  const [mapVisible, setMapVisible] = useState(true);
  const [clueDraft, setClueDraft] = useState('');
  const [numberDraft, setNumberDraft] = useState<ClueNumber>(1);

  const { you, teamNames, activeTeam, phase } = view;
  const activeName = teamNames[activeTeam];
  const isSpymaster = you.seat === 'spymaster';
  const isActiveSpymaster = isSpymaster && you.team === activeTeam;
  const isActiveOperative = you.seat === 'operative' && you.team === activeTeam;
  const finished = phase === 'finished';
  const showKey = finished || (isSpymaster && mapVisible);
  const canPenaltyReveal = isActiveSpymaster && phase === 'clue' && view.penaltyRevealPending && mapVisible;

  async function submitClue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (await act({ type: 'give-clue', word: clueDraft, number: numberDraft })) setClueDraft('');
  }

  function chooseCard(card: CardView) {
    if (canPenaltyReveal) void act({ type: 'penalty-reveal', cardId: card.id });
    else void act({ type: 'guess', cardId: card.id });
  }

  function renderCard(card: CardView, index: number) {
    const keyed = showKey && card.role !== null && !card.revealed;
    const isPenaltyTarget = canPenaltyReveal && card.role === activeTeam && !card.revealed;
    const selectable = (phase === 'guessing' && isActiveOperative && !card.revealed) || isPenaltyTarget;
    const cardClass = ['word-card', card.revealed && card.role ? `revealed-${card.role}` : '', keyed ? `key-${card.role}` : ''].filter(Boolean).join(' ');
    const label = card.revealed && card.role
      ? `${card.word}, revealed as ${roleLabel(card.role, teamNames)}`
      : `${card.word}${keyed && card.role ? `, hidden role: ${roleLabel(card.role, teamNames)}` : ''}${isPenaltyTarget ? ', select for penalty reveal' : ''}`;

    return (
      <button
        key={card.id}
        type="button"
        className={cardClass}
        disabled={!selectable}
        onClick={() => chooseCard(card)}
        aria-label={label}
        data-testid={`card-word-${card.id}`}
        data-role={card.revealed ? card.role ?? undefined : undefined}
      >
        <span className="card-index">{String(index + 1).padStart(2, '0')}</span>
        <span>{card.word}</span>
      </button>
    );
  }

  const banner = (() => {
    if (finished) return null;
    if (isActiveSpymaster && phase === 'clue') return { icon: <KeyRound size={18} />, title: `Transmit a clue · ${activeName}`, copy: 'Your operatives are standing by. Only your screen shows the map.' };
    if (isActiveOperative && phase === 'guessing') return { icon: <Users size={18} />, title: `Make contact · ${activeName}`, copy: 'Talk it through. Tap a word, or go dark after a correct guess.' };
    if (phase === 'clue') return { icon: <KeyRound size={18} />, title: `${activeName} spymaster is encoding`, copy: view.reviewPending ? 'Their clue is being vetted by the rival spymaster.' : 'Hold position. The transmission will appear here.' };
    return { icon: <Users size={18} />, title: `${activeName} operatives are in the field`, copy: 'Watch the board. It’s not your move.' };
  })();

  return (
    <main className="layout">
      <div className="game-layout">
        <section className="game-main" aria-label={isSpymaster ? 'Spymaster board' : 'Operative word board'}>
          <div className="game-head">
            <div>
              <div className="game-kicker"><span className="live-dot" /> You are {seatLabel(you, teamNames)}</div>
              <h1 className="game-title">{finished ? 'Declassified' : isSpymaster ? `${you.team ? teamNames[you.team] : ''} spymaster` : 'Field board'}</h1>
            </div>
            <div className="turn-badge" style={teamStyle(activeTeam)}>
              <span className="team-dot" /> {finished ? 'Mission over' : `${activeName} move`}
            </div>
          </div>
          <ScoreStrip view={view} />
          {finished && (
            <section className="result-panel" data-testid="status-game-result">
              <div className="eyebrow">Mission report</div>
              <h2>{view.winner ? `${teamNames[view.winner]} wins the op.` : 'The file is closed.'}</h2>
              <p>{view.resultMessage}</p>
              <span className="result-stamp" aria-hidden="true">Case closed</span>
            </section>
          )}
          {banner && (
            <div className="phase-banner" style={teamStyle(activeTeam)}>
              {banner.icon}
              <div><b>{banner.title}</b><span>{banner.copy}</span></div>
            </div>
          )}
          {isActiveSpymaster && view.penaltyRevealPending && phase === 'clue' && (
            <div className="bonus-callout" data-testid="status-penalty-reveal">
              <strong>Clue penalty</strong> tap one unrevealed {activeName} card to reveal it, then give your clue.
              <div className="action-row" style={{ marginTop: 9 }}>
                {!mapVisible && <button className="secondary-button" type="button" onClick={() => setMapVisible(true)} data-testid="button-show-penalty-map">Show the map</button>}
                <button className="secondary-button" type="button" onClick={() => act({ type: 'skip-penalty' })} data-testid="button-skip-penalty">Skip reveal</button>
              </div>
            </div>
          )}
          {isSpymaster && !finished && (
            <div className="spymaster-tools">
              <span className="role-pill">Secret map {mapVisible ? 'exposed' : 'concealed'}</span>
              <button type="button" className="quiet-button" onClick={() => setMapVisible(!mapVisible)} aria-pressed={mapVisible} data-testid="button-toggle-key">
                {mapVisible ? <><EyeOff size={15} /> Cover map</> : <><Eye size={15} /> Show map</>}
              </button>
            </div>
          )}
          <div className="board" role="group" aria-label={showKey ? 'Word board with secret roles' : 'Word board'}>
            {view.cards.map(renderCard)}
          </div>
          <div className="board-legend">
            <span>25 cover names · {view.cards.filter((card) => card.revealed).length} exposed</span>
            <span>{finished ? 'All identities declassified' : isSpymaster ? 'Guard your screen' : 'Trust no one'}</span>
          </div>
        </section>

        <aside className="game-side">
          {finished ? (
            <section className="panel side-panel">
              <h2 className="side-title">New assignment?</h2>
              {you.isHost ? (
                <>
                  <p className="turn-copy">A new mission deals 25 fresh cover names from the same word list, and a new secret map. Everyone keeps their post. You can change the words in the lobby.</p>
                  <button type="button" className="primary-button" style={{ width: '100%' }} onClick={() => act({ type: 'new-game' })} data-testid="button-new-game-finished">
                    New game <RotateCcw size={14} />
                  </button>
                </>
              ) : (
                <p className="turn-copy">The handler can start a new mission. You’ll be back in the briefing room with your post.</p>
              )}
            </section>
          ) : isActiveSpymaster && phase === 'clue' ? (
            <section className="panel side-panel" aria-labelledby="clue-form-title">
              <h2 className="side-title" id="clue-form-title">{view.penaltyRevealPending ? 'Reveal first' : view.reviewPending ? 'Clue under review' : 'Transmit a clue'}</h2>
              {view.penaltyRevealPending ? (
                <p className="turn-copy">Use the map to choose one friendly card for the penalty reveal, or skip it.</p>
              ) : view.reviewPending ? (
                <p className="turn-copy" data-testid="status-review-waiting">
                  “{view.review?.word}” matches a word on the board. The {teamNames[otherTeam(activeTeam)]} spymaster is deciding whether it stands.
                </p>
              ) : (
                <>
                  <p className="turn-copy">Choose one word and a number. The number sets your target, plus one extra guess.</p>
                  <form className="clue-form" onSubmit={submitClue}>
                    <label className="sr-only" htmlFor="clue-input">One-word clue</label>
                    <input id="clue-input" value={clueDraft} onChange={(event) => setClueDraft(event.target.value)} placeholder="Codeword" autoComplete="off" maxLength={32} data-testid="input-clue" />
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
                    <button type="submit" className="primary-button" data-testid="button-submit-clue">Transmit <ArrowRight size={14} /></button>
                  </form>
                  <p className="turn-copy" style={{ marginTop: 11, marginBottom: 0 }}>A zero or unlimited clue has no numeric cap. Operatives must make one guess before stopping.</p>
                </>
              )}
            </section>
          ) : (
            <section className="panel side-panel" aria-labelledby="current-clue-title">
              {phase === 'guessing' ? (
                <>
                  <CurrentClue view={view} titleId="current-clue-title" />
                  <div className="turn-copy" style={{ marginTop: 15 }}>
                    {view.guessesRemaining === 'unlimited'
                      ? 'No numeric cap. One correct guess is required before stopping.'
                      : `${view.guessesRemaining} ${view.guessesRemaining === 1 ? 'guess' : 'guesses'} left in this clue budget.`}
                  </div>
                  {isActiveOperative && (
                    <>
                      <button type="button" className="secondary-button" style={{ width: '100%', marginTop: 4 }} onClick={() => act({ type: 'end-turn' })} disabled={view.turnGuesses < 1} data-testid="button-stop-turn">
                        Go dark <Flag size={14} />
                      </button>
                      {view.turnGuesses < 1 && <p className="turn-copy" style={{ margin: '9px 0 0', fontSize: 10 }}>One guess minimum before you can go dark.</p>}
                    </>
                  )}
                </>
              ) : (
                <>
                  <h2 className="side-title" id="current-clue-title">Awaiting transmission</h2>
                  <p className="turn-copy">The {activeName} spymaster is encoding a word. It will appear on every screen at once.</p>
                </>
              )}
            </section>
          )}
          <PlayersPanel view={view} act={act} />
          <HistoryPanel view={view} />
        </aside>
      </div>
    </main>
  );
}

function ReviewDialog({ view, act }: { view: RoomView; act: Act }) {
  const review = view.review;
  const isJudge = review !== null && view.phase === 'clue' && view.you.seat === 'spymaster' && view.you.team === otherTeam(review.team);
  if (!isJudge) return null;
  return (
    <div className="overlay" role="presentation">
      <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="review-title">
        <div className="dialog-head">
          <div>
            <div className="eyebrow">Counterintelligence</div>
            <h2 id="review-title">Vet this clue</h2>
          </div>
        </div>
        <p className="dialog-copy">
          The {view.teamNames[review.team]} spymaster’s clue matches a hidden word or part of a compound word. Meaning and house rules are yours to judge; this check is only a prompt, not an automatic ruling.
        </p>
        <div className="word-check" data-testid="text-questionable-clue">“{review.word}” · {review.number === 'unlimited' ? '∞' : review.number}</div>
        <div className="dialog-actions">
          <button type="button" className="secondary-button" onClick={() => act({ type: 'review-clue', uphold: false })} data-testid="button-accept-clue"><Check size={14} /> Accept clue</button>
          <button type="button" className="danger-button" onClick={() => act({ type: 'review-clue', uphold: true })} data-testid="button-flag-clue"><Flag size={14} /> Uphold penalty</button>
        </div>
      </section>
    </div>
  );
}

function RulesDialog({ onClose }: { onClose: () => void }) {
  return (
    <div className="overlay" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
        <div className="dialog-head">
          <div>
            <div className="eyebrow">Field manual</div>
            <h2 id="dialog-title">Rules of engagement</h2>
          </div>
          <button type="button" className="icon-button" aria-label="Close dialog" onClick={onClose} data-testid="button-close-dialog"><X size={17} /></button>
        </div>
        <p className="dialog-copy">Two teams, each with one spymaster and some operatives. Everyone joins the same room on their own device.</p>
        <ul className="rule-list">
          <li>The spymaster gives a single-word clue and a number. The number means that many targets, plus one extra guess.</li>
          <li>Operatives must guess once. A friendly agent keeps the turn going; a neutral or rival card passes the turn.</li>
          <li>A zero or unlimited clue has no numeric cap. Stop after any correct guess if you have already guessed once.</li>
          <li>Find every friendly agent to win. The assassin ends the game immediately for the other team.</li>
          <li>If a clue matches a word on the board, the opposing spymaster decides on their own screen whether it stands.</li>
          <li>In the lobby, the host picks the word packs and can add the table’s own words. Custom words always make the board.</li>
        </ul>
        <p className="dialog-copy">Only spymasters’ devices receive the secret map, so there’s nothing for operatives to peek at.</p>
        <div className="dialog-actions"><button type="button" className="primary-button" onClick={onClose} data-testid="button-rules-got-it">Understood</button></div>
      </section>
    </div>
  );
}

function ConnectionBanner({ connection }: { connection: Connection }) {
  return (
    <div className="connection-banner" role="status" data-testid="status-connection">
      <WifiOff size={14} /> {connection === 'reconnecting' ? 'Signal lost. Re-establishing contact…' : 'Connecting…'}
    </div>
  );
}

function LostRoom({ onHome }: { onHome: () => void }) {
  return (
    <main className="layout">
      <section className="panel handoff-card" data-testid="status-room-lost">
        <div className="handoff-icon"><WifiOff size={28} /></div>
        <h1 className="handoff-title">Operation terminated.</h1>
        <p className="handoff-copy">The operation has ended or your cover is no longer valid. Ask the handler for a new access code.</p>
        <button type="button" className="primary-button" onClick={onHome} data-testid="button-back-home">Return to base <ArrowRight size={15} /></button>
      </section>
    </main>
  );
}

function ScoreStrip({ view }: { view: RoomView }) {
  const { remaining, totals, teamNames } = view;
  return (
    <section className="score-strip" aria-label="Remaining agents">
      <div className="score-team" style={teamStyle('red')}>
        <span className="team-dot" />
        <div className="score-block"><div className="score-name" data-testid="text-team-name-red">{teamNames.red}</div><span className="score-sub">{totals.red - remaining.red} contacted</span></div>
        <span className="score-number" data-testid="count-team-red">{remaining.red}</span>
      </div>
      <span className="versus">AT LARGE</span>
      <div className="score-team" style={teamStyle('blue')}>
        <span className="score-number" data-testid="count-team-blue">{remaining.blue}</span>
        <div className="score-block"><div className="score-name" data-testid="text-team-name-blue">{teamNames.blue}</div><span className="score-sub">{totals.blue - remaining.blue} contacted</span></div>
        <span className="team-dot" />
      </div>
    </section>
  );
}

function CurrentClue({ view, titleId }: { view: RoomView; titleId?: string }) {
  const clue = view.clue;
  const numberText = clue?.number === 'unlimited' ? '∞' : clue?.number;
  return (
    <div className="clue-entry" data-testid="panel-current-clue">
      <div className="clue-label" id={titleId}>Intercepted · {view.teamNames[clue?.team ?? view.activeTeam]}</div>
      <div className="clue-word" data-testid="text-current-clue">{clue?.word ?? 'Waiting for a clue'} <span className="clue-number" data-testid="text-clue-number">{numberText}</span></div>
      {view.guessesRemaining !== 0 && (
        <div className="guess-track" aria-label={`${view.guessesRemaining} guesses remaining`}>
          {Array.from({ length: 10 }, (_, index) => {
            const open = view.guessesRemaining === 'unlimited' ? index < Math.max(view.turnGuesses + 1, 3) : index < view.guessesRemaining;
            return <span key={index} className={`guess-pip ${open ? 'open' : 'used'}`} />;
          })}
          {view.guessesRemaining === 'unlimited' && <span className="clue-label">open-ended</span>}
        </div>
      )}
    </div>
  );
}

function PlayersPanel({ view, act }: { view: RoomView; act: Act }) {
  return (
    <section className="panel side-panel" aria-labelledby="players-title">
      <h2 className="side-title" id="players-title">Agents on the line</h2>
      <div className="players-list">
        {view.players.map((player) => (
          <div key={player.id} className={`player-row${player.connected ? '' : ' offline'}`} style={player.team ? teamStyle(player.team) : undefined}>
            <span className={`presence-dot${player.connected ? ' online' : ''}`} aria-label={player.connected ? 'Online' : 'Offline'} />
            <span className="player-name">{player.name}{player.id === view.you.id && ' (you)'}</span>
            <span className="player-seat">{player.team && player.seat ? `${view.teamNames[player.team]} ${player.seat}` : 'observer'}</span>
          </div>
        ))}
      </div>
      {!view.you.seat && view.phase !== 'finished' && (
        <div className="action-row" style={{ marginTop: 12 }}>
          {(['red', 'blue'] as const).map((team) => (
            <button key={team} type="button" className="secondary-button" onClick={() => act({ type: 'take-seat', team, seat: 'operative' })} data-testid={`button-join-late-${team}`}>
              Join {view.teamNames[team]}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

function HistoryPanel({ view }: { view: RoomView }) {
  return (
    <section className="panel side-panel history-panel" aria-labelledby="history-title">
      <div className="section-heading" style={{ marginBottom: 12 }}>
        <h2 className="side-title" id="history-title" style={{ margin: 0 }}>Signal log</h2>
        <span className="role-pill" data-testid="text-clue-count">{view.clueHistory.length} {view.clueHistory.length === 1 ? 'clue' : 'clues'}</span>
      </div>
      {view.clueHistory.length ? (
        <div className="history-list" data-testid="list-clue-history">
          {[...view.clueHistory].reverse().map((record) => (
            <div className="history-row" key={`${record.turn}-${record.team}-${record.word}`} data-testid={`row-clue-${record.turn}`}>
              <span className="history-dot" style={teamStyle(record.team)} />
              <div><span className="history-clue">{record.word}</span><span className="history-meta">{view.teamNames[record.team]} · turn {record.turn}</span></div>
              <span className="history-count">{record.number === 'unlimited' ? '∞' : record.number}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-history" data-testid="empty-clue-history">No transmissions yet. The first spymaster is waiting to make contact.</div>
      )}
    </section>
  );
}

export default App;
