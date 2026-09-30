import { useEffect, useRef, useState, type CSSProperties, type FormEvent, type ReactNode } from 'react';
import {
  ArrowRight,
  BookOpen,
  Check,
  CircleX,
  Copy,
  Crown,
  Eye,
  EyeOff,
  Flag,
  Library,
  Pencil,
  Radio,
  RotateCcw,
  SatelliteDish,
  Shield,
  Skull,
  WifiOff,
  X,
} from 'lucide-react';
import {
  clueConflict,
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
/** Sends an action; resolves to the updated view, or null if it was rejected. */
type Act = (action: Action) => Promise<RoomView | null>;
/** One line of the telemetry feed: what happened this session, newest last. */
type LogEntry = { id: number; text: string; time: string; team: Team | null };

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
  return { '--team-color': `var(--${team})`, '--team-tint': `var(--${team}-tint)`, '--team-edge': `var(--${team}-edge)` } as CSSProperties;
}

function clockTime() {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
}

function numberText(number: ClueNumber) {
  return number === 'unlimited' ? '∞' : String(number);
}

/** Which team an event line is about, so its dot can be colored. */
function eventTeam(text: string, teamNames: Record<Team, string>): Team | null {
  const lower = text.toLowerCase();
  const red = lower.indexOf(teamNames.red.toLowerCase());
  const blue = lower.indexOf(teamNames.blue.toLowerCase());
  if (red < 0 && blue < 0) return null;
  if (blue < 0) return 'red';
  if (red < 0) return 'blue';
  return red < blue ? 'red' : 'blue';
}

function App() {
  const [code, setCode] = useState<string | null>(codeFromLocation);
  const [seat, setSeat] = useState<Seat | null>(() => (code ? loadSeat(code) : null));
  const [toast, setToast] = useState<ToastMessage>(null);
  const [rulesOpen, setRulesOpen] = useState(false);
  const [sharedPack] = useState(packFromLocation);
  const [packsOpen, setPacksOpen] = useState(sharedPack !== null);
  const [log, setLog] = useState<LogEntry[]>([]);
  const logId = useRef(0);
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
    if (!view?.lastEvent) return;
    setToast(view.lastEvent);
    setLog((entries) => [...entries.slice(-39), { id: ++logId.current, text: view.lastEvent, time: clockTime(), team: eventTeam(view.lastEvent, view.teamNames) }]);
  }, [view?.lastEvent]);

  // A fresh room or a new seat starts a fresh feed.
  useEffect(() => setLog([]), [seat?.code]);

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
    if (!seat) return null;
    try {
      const next = await sendAction(seat, action);
      accept(next);
      return next;
    } catch (error) {
      setToast((error as Error).message);
      return null;
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
  const canLeave = view && (view.phase === 'lobby' || !view.you.seat);
  const currentTab = !view ? null : view.phase === 'lobby' ? 'Lobby' : view.you.seat === 'spymaster' ? 'Spymaster' : 'Operative';

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          {canLeave ? (
            <button type="button" className="brand-exit" aria-label="Leave room" onClick={leave} data-testid="button-leave-room"><CircleX size={24} strokeWidth={2} /></button>
          ) : (
            <span className="brand-exit" aria-hidden="true"><CircleX size={24} strokeWidth={2} /></span>
          )}
          <span className="brand-name">Word Agents</span>
          {view && <span className="room-pill" data-testid="text-room-code">Room: {view.code}</span>}
        </div>
        {currentTab && (
          <nav className="view-tabs" aria-label="Current view">
            {(['Lobby', 'Operative', 'Spymaster'] as const).map((tab) => (
              <span key={tab} className={`view-tab${tab === currentTab ? ' active' : ''}`} aria-current={tab === currentTab ? 'page' : undefined}>{tab}</span>
            ))}
          </nav>
        )}
        <div className="top-actions">
          {view && <span className="agent-id">Agent ID: <b>{view.you.name}</b></span>}
          {view && isHost && view.phase !== 'lobby' && (
            <button type="button" className="icon-button" aria-label="New game" title="New game" onClick={() => act({ type: 'new-game' })} data-testid="button-new-game">
              <RotateCcw size={16} />
            </button>
          )}
          <button type="button" className="icon-button" aria-label="Word packs" title="Word packs" onClick={() => setPacksOpen(true)} data-testid="button-open-packs">
            <Library size={16} />
          </button>
          <button type="button" className="icon-button" aria-label="Read the rules" title="Rules" onClick={() => setRulesOpen(true)} data-testid="button-open-rules">
            <BookOpen size={16} />
          </button>
        </div>
      </header>

      {!seat && <Home initialCode={code} onSeat={enterRoom} onError={setToast} />}
      {seat && connection === 'lost' && <LostRoom onHome={abandonSeat} />}
      {seat && connection !== 'lost' && !view && <main className="layout narrow"><div className="panel center-card"><p className="muted-copy loading-copy">Establishing secure line</p></div></main>}
      {seat && connection !== 'lost' && view && (
        <>
          {connection === 'reconnecting' && <ConnectionBanner connection={connection} />}
          {view.phase === 'lobby' ? <Lobby view={view} act={act} onToast={setToast} onManagePacks={() => setPacksOpen(true)} /> : <Table view={view} act={act} log={log} />}
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
    <main className="layout home-layout">
      <section className="home-intro" aria-labelledby="welcome-title">
        <span className="mono-label gold">Classified briefing · Eyes only</span>
        <h1 className="home-title" id="welcome-title">Crack the code.<br /><span>Find your agents.</span></h1>
        <p className="muted-copy">
          One clue. Twenty-five cover names. Two rival squads trying to think on the same wavelength.
          Every agent plays on their own phone, and only spymasters ever receive the secret map.
        </p>
        <ul className="home-points">
          <li><b>Need to know</b><span>Each spymaster sees the map on their own screen. Nobody else’s device receives it.</span></li>
          <li><b>Debrief together</b><span>Operatives decode the clue, argue it out, then tap a word on their phone.</span></li>
          <li><b>Avoid the assassin</b><span>Contact your whole squad first. One wrong word ends the mission.</span></li>
        </ul>
      </section>

      <section className="home-side">
        <h2 className="section-title">{initialCode ? `Join operation ${initialCode}` : 'Report for duty'}</h2>
        <div className="panel form-panel" aria-label="Join an operation">
          <div className="field">
            <label className="mono-label" htmlFor="player-name">Step 1: Codename</label>
            <input id="player-name" className="text-input" value={name} onChange={(event) => setName(event.target.value)} maxLength={24} autoComplete="nickname" placeholder="e.g. SPY_FOX_99" data-testid="input-player-name" />
          </div>
          <form className="field" onSubmit={submitJoin}>
            <label className="mono-label" htmlFor="room-code">Step 2: Access code</label>
            <div className="input-with-action">
              <input
                id="room-code"
                className="text-input mono"
                value={joinCode}
                onChange={(event) => setJoinCode(event.target.value.toUpperCase())}
                maxLength={8}
                autoComplete="off"
                placeholder="ABCDE"
                data-testid="input-room-code"
              />
              <button type="submit" className="chip-button" disabled={busy} data-testid="button-join-room">Join</button>
            </div>
          </form>
          <p className="muted-copy small">Your cover is remembered on this device.</p>
        </div>
        {!initialCode && (
          <div className="panel launch-panel">
            <p className="muted-copy small center"><Shield size={13} aria-hidden="true" /> Running the mission? Open a room and share the code with your crew.</p>
            <button type="button" className="gold-button" disabled={busy} onClick={() => run(() => createRoom(name.trim()))} data-testid="button-create-room">
              Open an operation
            </button>
          </div>
        )}
      </section>
    </main>
  );
}

function Avatar({ player }: { player: PlayerView }) {
  const initials = player.name.replace(/[^A-Za-z0-9 ]/g, '').split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || '?';
  return (
    <span className={`avatar${player.connected ? '' : ' offline'}`} style={player.team ? teamStyle(player.team) : undefined}>
      {initials}
      <span className={`presence-dot${player.connected ? ' online' : ''}`} aria-label={player.connected ? 'Online' : 'Offline'} />
    </span>
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
    <main className="layout lobby-layout">
      <section className="lobby-roster" aria-labelledby="roster-title">
        <div className="section-head">
          <h1 className="section-title" id="roster-title">Field agent roster</h1>
          <div className="share-row">
            <span className="room-code-big" data-testid="text-room-code-big">{view.code}</span>
            <button type="button" className="chip-button" onClick={copyLink} data-testid="button-copy-link"><Copy size={13} /> Copy link</button>
          </div>
        </div>
        <div className="squad-grid">
          {(['red', 'blue'] as const).map((team) => {
            const members = view.players.filter((player) => player.team === team);
            const spymaster = members.find((player) => player.seat === 'spymaster');
            const operatives = members.filter((player) => player.seat === 'operative');
            const youOperativeHere = view.you.team === team && view.you.seat === 'operative';
            return (
              <section key={team} className="panel squad" style={teamStyle(team)} aria-label={`${view.teamNames[team]} team`}>
                <div className="squad-head">
                  {view.you.isHost ? (
                    <input
                      className="squad-name-input"
                      value={names[team]}
                      maxLength={18}
                      onChange={(event) => setNames({ ...names, [team]: event.target.value })}
                      onBlur={() => commitName(team)}
                      onKeyDown={(event) => event.key === 'Enter' && event.currentTarget.blur()}
                      aria-label={`Rename team ${team}`}
                      data-testid={`input-team-${team}`}
                    />
                  ) : (
                    <h2 className="squad-name" data-testid={`text-team-name-${team}`}>{view.teamNames[team]} squad</h2>
                  )}
                  <span className="count-chip">{members.length} {members.length === 1 ? 'member' : 'members'}</span>
                </div>
                {view.startingTeam === team && <span className="mono-label squad-first">Makes the first move</span>}
                <div className="squad-rule" />
                <div className="member-list">
                  {spymaster ? (
                    <MemberRow player={spymaster} you={view.you} />
                  ) : (
                    <button type="button" className="dashed-button" onClick={() => act({ type: 'take-seat', team, seat: 'spymaster' })} data-testid={`button-seat-${team}-spymaster`}>
                      + Claim spymaster seat
                    </button>
                  )}
                  {operatives.map((player) => <MemberRow key={player.id} player={player} you={view.you} />)}
                </div>
                {!youOperativeHere && (
                  <button type="button" className="dashed-button" onClick={() => act({ type: 'take-seat', team, seat: 'operative' })} data-testid={`button-seat-${team}-operative`}>
                    + Join {view.teamNames[team]} squad
                  </button>
                )}
              </section>
            );
          })}
        </div>

        {unseated.length > 0 && (
          <section className="panel unseated-panel">
            <span className="mono-label">Awaiting assignment</span>
            <div className="member-chips">{unseated.map((player) => <MemberRow key={player.id} player={player} you={view.you} />)}</div>
          </section>
        )}
      </section>

      <section className="lobby-settings" aria-labelledby="settings-title">
        <h2 className="section-title" id="settings-title">Tactical intel settings</h2>
        <WordsPanel view={view} act={act} onToast={onToast} onManagePacks={onManagePacks} />
        <div className="panel launch-panel">
          <p className="muted-copy small center" data-testid="text-seating-status">
            {problem ?? 'All spymasters are locked in. Prepared for deployment.'}
          </p>
          {view.you.isHost ? (
            <button type="button" className="gold-button" disabled={Boolean(problem)} onClick={() => act({ type: 'start' })} data-testid="button-start-game">
              Authorize game launch
            </button>
          ) : (
            <span className="waiting-pill">Awaiting the host’s authorization</span>
          )}
          {view.you.seat && (
            <button type="button" className="text-button" onClick={() => act({ type: 'leave-seat' })} data-testid="button-leave-seat">Stand down from my seat</button>
          )}
        </div>
      </section>
    </main>
  );
}

function MemberRow({ player, you }: { player: PlayerView; you: PlayerView }) {
  const isSpymaster = player.seat === 'spymaster';
  return (
    <div className={`member-row${isSpymaster ? ' is-spymaster' : ''}${player.connected ? '' : ' offline'}`} data-testid={`row-player-${player.id}`}>
      <Avatar player={player} />
      <span className="member-name">{player.name}{player.id === you.id && <span className="you-tag"> (you)</span>}</span>
      {player.isHost && <Crown size={13} className="host-icon" aria-label="Host" />}
      {player.seat && <span className={`role-chip${isSpymaster ? ' spymaster' : ''}`}>{player.seat}</span>}
    </div>
  );
}

function Table({ view, act, log }: { view: RoomView; act: Act; log: LogEntry[] }) {
  const [mapVisible, setMapVisible] = useState(true);
  const [clueDraft, setClueDraft] = useState('');
  // Digits only, 0 to 9 (the server's range). Kept as text so the field can be empty while typing.
  const [numberDraft, setNumberDraft] = useState('');
  // Tapped cards are only marked, in tap order; nothing is sent until the picks are submitted.
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  // The latest picks, so taps landing before a re-render still build on each other.
  const picks = useRef<string[]>([]);
  const [pickHint, setPickHint] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { you, teamNames, activeTeam, phase } = view;
  const activeName = teamNames[activeTeam];
  const isSpymaster = you.seat === 'spymaster';
  const isActiveSpymaster = isSpymaster && you.team === activeTeam;
  const isActiveOperative = you.seat === 'operative' && you.team === activeTeam;
  const finished = phase === 'finished';
  const showKey = finished || (isSpymaster && mapVisible);
  const canPenaltyReveal = isActiveSpymaster && phase === 'clue' && view.penaltyRevealPending && mapVisible;
  const isGuessing = phase === 'guessing' && isActiveOperative;

  const isPenaltyTarget = (card: CardView) => canPenaltyReveal && card.role === activeTeam && !card.revealed;
  const isSelectable = (card: CardView) => (isGuessing && !card.revealed) || isPenaltyTarget(card);
  // Derived, so a mark disappears by itself once the card is revealed or the turn moves on.
  const selected = selectedIds
    .map((id) => view.cards.find((card) => card.id === id))
    .filter((card): card is CardView => card !== undefined && isSelectable(card));
  const hasSelection = selected.length > 0;
  // A numeric clue caps how many picks can go in at once (the number, plus one).
  const maxPicks = canPenaltyReveal ? 1 : view.guessesRemaining === 'unlimited' ? Infinity : view.guessesRemaining;
  const clueProblem = clueConflict(clueDraft, view.cards);

  function setPicks(ids: string[]) {
    picks.current = ids;
    setSelectedIds(ids);
  }

  function clearSelection() {
    setPicks([]);
    setPickHint(null);
  }

  useEffect(() => {
    if (!hasSelection) return undefined;
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && clearSelection();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [hasSelection]);

  async function submitClue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (numberDraft === '' || clueProblem) return;
    if (await act({ type: 'give-clue', word: clueDraft, number: Number(numberDraft) })) {
      setClueDraft('');
      setNumberDraft('');
    }
  }

  function markCard(card: CardView) {
    const ids = picks.current.filter((id) => view.cards.some((item) => item.id === id && isSelectable(item)));
    setPickHint(null);
    if (ids.includes(card.id)) return setPicks(ids.filter((id) => id !== card.id));
    // A penalty reveal is always a single card: a new tap moves the mark.
    if (canPenaltyReveal) return setPicks([card.id]);
    if (ids.length >= maxPicks) {
      setPicks(ids);
      return setPickHint(`Max ${maxPicks} ${maxPicks === 1 ? 'pick' : 'picks'} on this clue`);
    }
    setPicks([...ids, card.id]);
  }

  const submitPicks = () => act({ type: 'guesses', cardIds: selected.map((card) => card.id) });

  async function confirmCard() {
    if (!hasSelection || submitting) return;
    setSubmitting(true);
    const ok = await (canPenaltyReveal ? act({ type: 'penalty-reveal', cardId: selected[0].id }) : submitPicks());
    setSubmitting(false);
    if (ok) clearSelection();
  }

  // End turn submits the marked words (if any), then passes the turn if the guesses didn't already.
  async function endTurn() {
    if (submitting) return;
    setSubmitting(true);
    if (hasSelection) {
      const next = await submitPicks();
      if (next) clearSelection();
      if (next?.phase === 'guessing' && next.activeTeam === activeTeam) await act({ type: 'end-turn' });
    } else {
      await act({ type: 'end-turn' });
    }
    setSubmitting(false);
  }

  function cardState(card: CardView): { tone: string; label: string } {
    const pick = selected.findIndex((item) => item.id === card.id);
    if (pick >= 0) {
      const label = canPenaltyReveal ? 'Reveal target' : selected.length > 1 ? `Pick ${pick + 1}` : 'Target marked';
      return { tone: showKey && card.role ? `key-${card.role} is-selected` : 'is-selected', label };
    }
    if (showKey && card.role) {
      if (card.revealed) return { tone: 'resolved', label: 'Resolved' };
      if (card.role === 'assassin') return { tone: 'key-assassin', label: 'Assassin' };
      if (card.role === 'neutral') return { tone: 'key-neutral', label: 'Bystander' };
      return { tone: `key-${card.role}`, label: `${teamNames[card.role]} agent` };
    }
    if (card.revealed && card.role) {
      if (card.role === 'assassin') return { tone: 'revealed-assassin', label: 'Assassin' };
      if (card.role === 'neutral') return { tone: 'revealed-neutral', label: 'Bystander' };
      return { tone: `revealed-${card.role}`, label: `${teamNames[card.role]} agent` };
    }
    return { tone: '', label: 'Unrevealed' };
  }

  function renderCard(card: CardView) {
    const keyed = showKey && card.role !== null && !card.revealed;
    const penaltyTarget = isPenaltyTarget(card);
    const selectable = isSelectable(card);
    const isSelected = selected.some((item) => item.id === card.id);
    const { tone, label } = cardState(card);
    const assassin = tone.includes('assassin');
    const aria = card.revealed && card.role
      ? `${card.word}, revealed as ${roleLabel(card.role, teamNames)}`
      : `${card.word}${keyed && card.role ? `, hidden role: ${roleLabel(card.role, teamNames)}` : ''}${penaltyTarget ? ', select for penalty reveal' : ''}`;

    return (
      <button
        key={card.id}
        type="button"
        className={`word-card ${tone}`.trim()}
        disabled={!selectable}
        onClick={() => markCard(card)}
        aria-pressed={selectable ? isSelected : undefined}
        aria-label={aria}
        data-testid={`card-word-${card.id}`}
        data-role={card.revealed ? card.role ?? undefined : undefined}
      >
        {!assassin && <span className="card-label">{label}</span>}
        <span className="card-word">{card.word}</span>
        {assassin ? (
          <span className="card-label assassin-label"><span className="skull-badge"><Skull size={9} strokeWidth={2.5} /></span>{label}</span>
        ) : (
          <span className="card-rule" aria-hidden="true"><i /><b /><i /></span>
        )}
      </button>
    );
  }

  const phaseText = finished
    ? 'Mission complete'
    : phase === 'clue'
      ? `${activeName} spymaster ${view.reviewPending ? 'under review' : 'encoding'}`
      : `${activeName} operatives guessing`;
  const turnNumber = view.clueHistory.length + (phase === 'clue' ? 1 : 0);
  const assassinCard = view.cards.find((card) => card.role === 'assassin');

  return (
    <>
      <div className="status-bar" style={teamStyle(activeTeam)}>
        <div className="status-phase"><span className="team-dot" /> {phaseText}</div>
        <span className="timer-pill"><Radio size={15} /> Turn {Math.max(turnNumber, 1)}</span>
        <ScoreStrip view={view} detailed={isSpymaster || finished} />
      </div>

      <main className="layout game-layout">
        <section className="game-main" aria-label={isSpymaster ? 'Spymaster board' : 'Operative word board'}>
          <h1 className="sr-only">{finished ? 'Declassified' : isSpymaster ? 'Spymaster board' : 'Field board'} · You are {seatLabel(you, teamNames)}</h1>

          {finished && (
            <section className="broadcast result-broadcast" data-testid="status-game-result">
              <div className="broadcast-body">
                <span className="broadcast-icon"><Flag size={20} /></span>
                <div>
                  <span className="mono-label gold">Mission report</span>
                  <div className="broadcast-word">{view.winner ? `${teamNames[view.winner]} wins the op` : 'The file is closed'}</div>
                  <span className="muted-copy small">{view.resultMessage}</span>
                </div>
              </div>
            </section>
          )}

          {!finished && (!isSpymaster || phase === 'guessing') && (
            <ClueBroadcast view={view}>
              {isGuessing && (
                <div className="guess-actions" role="group" aria-label="Confirm your guesses" data-testid="panel-guess-confirm">
                  <div className="guess-target" aria-live="polite">
                    <span className={`mono-label${pickHint ? ' gold' : ''}`} data-testid="text-pick-status">
                      {pickHint ?? (hasSelection ? `${selected.length} ${selected.length === 1 ? 'target' : 'targets'} marked` : 'No target marked')}
                    </span>
                    <b data-testid="text-selected-card" title={selected.map((card) => card.word).join(', ')}>
                      {hasSelection ? selected.map((card) => card.word).join(' · ') : 'Tap words to mark them'}
                    </b>
                  </div>
                  {hasSelection && (
                    <button type="button" className="icon-button" aria-label="Clear marked words" onClick={clearSelection} data-testid="button-clear-guess"><X size={16} /></button>
                  )}
                  {hasSelection && (
                    <button type="button" className="ghost-button" onClick={confirmCard} disabled={submitting} data-testid="button-submit-guess">
                      {selected.length > 1 ? `Submit ${selected.length}` : 'Submit'}
                    </button>
                  )}
                  <button
                    type="button"
                    className="solid-button"
                    onClick={endTurn}
                    disabled={submitting || (!hasSelection && view.turnGuesses < 1)}
                    title={!hasSelection && view.turnGuesses < 1 ? 'Mark a word first. You must make one guess before the turn can end.' : hasSelection ? 'Submits the marked words in order, then passes the turn.' : undefined}
                    data-testid="button-stop-turn"
                  >
                    End turn
                  </button>
                </div>
              )}
            </ClueBroadcast>
          )}

          {isSpymaster && !finished && (
            <div className="map-head">
              <h2 className="section-title">Tactical master map (spymaster only)</h2>
              <div className="map-tools">
                <span className="legend"><i className="legend-dot neutral" /> Neutral <i className="legend-dot assassin" /> Assassin</span>
                <button type="button" className="chip-button" onClick={() => setMapVisible(!mapVisible)} aria-pressed={mapVisible} data-testid="button-toggle-key">
                  {mapVisible ? <><EyeOff size={13} /> Cover map</> : <><Eye size={13} /> Show map</>}
                </button>
              </div>
            </div>
          )}

          {isActiveSpymaster && view.penaltyRevealPending && phase === 'clue' && (
            <div className="alert-panel gold" data-testid="status-penalty-reveal">
              <span className="mono-label gold">Clue penalty</span>
              <p>Mark one unrevealed {activeName} card and confirm to reveal it, then give your clue.</p>
              <div className="action-row">
                {canPenaltyReveal && (
                  <button className="solid-button" type="button" disabled={!hasSelection || submitting} onClick={confirmCard} data-testid="button-confirm-penalty">
                    {hasSelection ? `Reveal ${selected[0].word}` : 'Mark a card'}
                  </button>
                )}
                {!mapVisible && <button className="ghost-button" type="button" onClick={() => setMapVisible(true)} data-testid="button-show-penalty-map">Show the map</button>}
                <button className="ghost-button" type="button" onClick={() => act({ type: 'skip-penalty' })} data-testid="button-skip-penalty">Skip reveal</button>
              </div>
            </div>
          )}

          <div className="board" role="group" aria-label={showKey ? 'Word board with secret roles' : 'Word board'}>
            {view.cards.map(renderCard)}
          </div>
        </section>

        <aside className="game-side">
          {finished ? (
            <>
              <h2 className="section-title">Next assignment</h2>
              <section className="panel side-panel">
                {you.isHost ? (
                  <>
                    <p className="muted-copy small">A new mission deals 25 fresh words from the same word list and a new secret map. Everyone keeps their seat.</p>
                    <button type="button" className="gold-button" onClick={() => act({ type: 'new-game' })} data-testid="button-new-game-finished">
                      <RotateCcw size={15} /> New game
                    </button>
                  </>
                ) : (
                  <p className="muted-copy small">The host can start a new mission. You’ll be back in the lobby with your seat.</p>
                )}
              </section>
            </>
          ) : isSpymaster ? (
            <>
              <h2 className="section-title" id="clue-form-title">Clue encryption panel</h2>
              <section className="panel side-panel" aria-labelledby="clue-form-title" style={teamStyle(you.team ?? activeTeam)}>
                {!isActiveSpymaster || phase !== 'clue' ? (
                  <p className="muted-copy small" data-testid="status-spymaster-waiting">
                    {phase === 'guessing'
                      ? `${activeName} operatives are working on the clue. Watch the map.`
                      : `The ${activeName} spymaster is encoding a clue${view.reviewPending ? ' that is being vetted' : ''}.`}
                  </p>
                ) : view.penaltyRevealPending ? (
                  <p className="muted-copy small">Use the map to reveal one of your own cards for the clue penalty, or skip it.</p>
                ) : view.reviewPending ? (
                  <p className="muted-copy small" data-testid="status-review-waiting">
                    “{view.review?.word}” is close to a word on the board. The {teamNames[otherTeam(activeTeam)]} spymaster is deciding whether it stands.
                  </p>
                ) : (
                  <form className="clue-form" onSubmit={submitClue}>
                    <div className="field">
                      <label className="mono-label" htmlFor="clue-input">Step 1: Entry keyword</label>
                      <div className="input-icon">
                        <input
                          id="clue-input"
                          className="text-input"
                          value={clueDraft}
                          onChange={(event) => setClueDraft(event.target.value)}
                          placeholder="One word"
                          autoComplete="off"
                          maxLength={32}
                          aria-invalid={clueProblem ? true : undefined}
                          aria-describedby={clueProblem ? 'clue-problem' : undefined}
                          data-testid="input-clue"
                        />
                        <Pencil size={16} aria-hidden="true" />
                      </div>
                      {clueProblem && <p className="field-error" id="clue-problem" role="alert" data-testid="text-clue-problem">{clueProblem}</p>}
                    </div>
                    <div className="field">
                      <label className="mono-label" htmlFor="clue-number">Step 2: Target number</label>
                      <input
                        id="clue-number"
                        className="text-input mono"
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]"
                        maxLength={1}
                        autoComplete="off"
                        placeholder="Enter target number (0–9)"
                        value={numberDraft}
                        onChange={(event) => setNumberDraft(event.target.value.replace(/\D/g, '').slice(-1))}
                        required
                        data-testid="input-clue-number"
                      />
                    </div>
                    <div className="divider" />
                    <div className="summary-box">
                      <span className="mono-label team">Transmission summary</span>
                      <p>
                        {clueDraft.trim() && numberDraft !== ''
                          ? <>You are submitting <b>{clueDraft.trim().toUpperCase()} — {numberDraft}</b> to your field agents. {numberDraft === '0' ? 'A zero clue has no guess limit.' : 'They get that many guesses, plus one extra.'}</>
                          : 'Enter a one-word clue and a number from 0 to 9. The number sets your target, plus one extra guess.'}
                      </p>
                    </div>
                    <button type="submit" className="team-button" disabled={!clueDraft.trim() || numberDraft === '' || Boolean(clueProblem)} data-testid="button-submit-clue">Encrypt &amp; transmit clue</button>
                  </form>
                )}
              </section>
              {assassinCard && !assassinCard.revealed && mapVisible && (
                <section className="alert-panel red">
                  <span className="mono-label red">Warning: assassin position</span>
                  <p>The word <b>{assassinCard.word}</b> is the assassin. Ensure your clue cannot be linked to it under any circumstance.</p>
                </section>
              )}
            </>
          ) : (
            <>
              <h2 className="section-title">Operation telemetry</h2>
              <Telemetry view={view} log={log} />
              <section className="panel side-panel">
                <span className="mono-label gold">Covert rules</span>
                <p className="muted-copy small">
                  Analyze the code word from your spymaster. Avoid the bystanders and stay far away from the <b className="danger">assassin</b>.
                  {phase === 'guessing' && isActiveOperative && view.turnGuesses < 1 && ' Make one guess before you can end the turn.'}
                </p>
              </section>
            </>
          )}
          <PlayersPanel view={view} act={act} />
          {(isSpymaster || finished) && <HistoryPanel view={view} />}
        </aside>
      </main>
    </>
  );
}

function ClueBroadcast({ view, children }: { view: RoomView; children?: ReactNode }) {
  const clue = view.phase === 'guessing' ? view.clue : null;
  const team = clue?.team ?? view.activeTeam;
  return (
    <section className="broadcast" style={teamStyle(team)} data-testid="panel-current-clue">
      <div className="broadcast-body">
        <span className="broadcast-icon"><SatelliteDish size={20} /></span>
        <div>
          <span className="mono-label team" id="current-clue-title">{clue ? `Active ${view.teamNames[team]} clue broadcast` : `Awaiting ${view.teamNames[team]} transmission`}</span>
          <div className="broadcast-word">
            <span data-testid="text-current-clue">{clue?.word ?? 'Standing by'}</span>
            {clue && <span className="broadcast-number" data-testid="text-clue-number">{numberText(clue.number)}</span>}
          </div>
          {clue && children && <span className="broadcast-meta">{view.guessesRemaining === 'unlimited' ? 'No numeric cap' : `${view.guessesRemaining} ${view.guessesRemaining === 1 ? 'guess' : 'guesses'} left`}</span>}
        </div>
      </div>
      {children}
      {clue && !children && (
        <span className="broadcast-meta">
          {view.guessesRemaining === 'unlimited'
            ? 'No numeric cap'
            : `${view.guessesRemaining} ${view.guessesRemaining === 1 ? 'guess' : 'guesses'} left`}
        </span>
      )}
    </section>
  );
}

function Telemetry({ view, log }: { view: RoomView; log: LogEntry[] }) {
  // Before anything happens this session, fall back to the clue history the server keeps.
  const entries = log.length
    ? log
    : view.clueHistory.map((record, index) => ({
        id: -index - 1,
        text: `${view.teamNames[record.team]} spymaster transmitted clue: ${record.word} — ${numberText(record.number)}`,
        time: `Turn ${record.turn}`,
        team: record.team,
      }));
  return (
    <section className="panel telemetry" aria-label="Live activity">
      <div className="telemetry-head">
        <span className="mono-label plain">Live activity transmissions</span>
        <span className="count-chip neutral" data-testid="text-clue-count">{view.clueHistory.length} {view.clueHistory.length === 1 ? 'clue' : 'clues'}</span>
      </div>
      {entries.length ? (
        <ul className="telemetry-list" data-testid="list-clue-history">
          {entries.map((entry) => (
            <li key={entry.id} style={entry.team ? teamStyle(entry.team) : undefined} className={entry.team ? 'has-team' : ''}>
              <span className="telemetry-dot" />
              <div><span>{entry.text}</span><time>{entry.time}</time></div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted-copy small" data-testid="empty-clue-history">No transmissions yet. The first spymaster is waiting to make contact.</p>
      )}
    </section>
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
            <span className="mono-label gold">Counterintelligence</span>
            <h2 id="review-title">Vet this clue</h2>
          </div>
        </div>
        <p className="dialog-copy">
          The {view.teamNames[review.team]} spymaster’s clue is close to a hidden word: it contains one, or sits inside one (like SHARKS for SHARK). Meaning and house rules are yours to judge; this check is only a prompt, not an automatic ruling.
        </p>
        <div className="word-check" data-testid="text-questionable-clue">“{review.word}” · {numberText(review.number)}</div>
        <div className="dialog-actions">
          <button type="button" className="ghost-button" onClick={() => act({ type: 'review-clue', uphold: false })} data-testid="button-accept-clue"><Check size={14} /> Accept clue</button>
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
            <span className="mono-label gold">Field manual</span>
            <h2 id="dialog-title">Rules of engagement</h2>
          </div>
          <button type="button" className="icon-button" aria-label="Close dialog" onClick={onClose} data-testid="button-close-dialog"><X size={16} /></button>
        </div>
        <p className="dialog-copy">Two teams, each with one spymaster and some operatives. Everyone joins the same room on their own device.</p>
        <ol className="rule-list">
          <li>The spymaster gives a single-word clue and a number. The number means that many targets, plus one extra guess.</li>
          <li>Operatives mark one or more words, then Submit to keep guessing or End turn to submit them and pass. Picks are revealed in the order you marked them. A friendly agent keeps the turn going; a neutral or rival card passes it and any remaining picks stay hidden.</li>
          <li>A zero or unlimited clue has no numeric cap. You must guess at least once before ending the turn.</li>
          <li>Find every friendly agent to win. The assassin ends the game immediately for the other team.</li>
          <li>A clue can’t be a word on the board, or part of one. If it’s close to one (like SHARKS for SHARK), the opposing spymaster decides on their own screen whether it stands.</li>
          <li>In the lobby, the host picks the word packs and can add the table’s own words. Custom words always make the board.</li>
        </ol>
        <p className="dialog-copy">Only spymasters’ devices receive the secret map, so there’s nothing for operatives to peek at.</p>
        <div className="dialog-actions"><button type="button" className="gold-button compact" onClick={onClose} data-testid="button-rules-got-it">Understood</button></div>
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
    <main className="layout narrow">
      <section className="panel center-card" data-testid="status-room-lost">
        <span className="lost-icon"><WifiOff size={26} /></span>
        <h1 className="section-title">Operation terminated</h1>
        <p className="muted-copy">The room has ended or your seat is no longer valid. Ask the host for a new code.</p>
        <button type="button" className="gold-button" onClick={onHome} data-testid="button-back-home">Return to base <ArrowRight size={15} /></button>
      </section>
    </main>
  );
}

function ScoreStrip({ view, detailed }: { view: RoomView; detailed: boolean }) {
  const { remaining, totals, teamNames } = view;
  const assassinLeft = view.cards.some((card) => card.role === 'assassin' && !card.revealed);
  return (
    <section className={`scoreboard${detailed ? ' detailed' : ''}`} aria-label="Remaining agents">
      {(['red', 'blue'] as const).map((team, index) => (
        <div key={team} className="score-group">
          {index > 0 && <span className="score-divider" aria-hidden="true" />}
          <div className="score-box" style={teamStyle(team)} title={`${totals[team] - remaining[team]} of ${totals[team]} found`}>
            {detailed && <span className="score-chip" data-testid={`text-team-name-${team}`}>{teamNames[team]}</span>}
            <span className="score-label">{detailed ? 'Agents' : <><span data-testid={`text-team-name-${team}`}>{teamNames[team]}</span> agents:</>}</span>
            <span className="score-value" data-testid={`count-team-${team}`}>{remaining[team]}</span>
          </div>
        </div>
      ))}
      {detailed && (
        <div className="score-box assassin-box">
          <span className="assassin-chip"><Skull size={14} /> Assassin</span>
          <span className="score-label">Danger</span>
          <span className="score-value">{assassinLeft ? 1 : 0}</span>
        </div>
      )}
    </section>
  );
}

function PlayersPanel({ view, act }: { view: RoomView; act: Act }) {
  return (
    <section className="panel side-panel" aria-labelledby="players-title">
      <span className="mono-label plain" id="players-title">Agents on the line</span>
      <div className="member-list compact">
        {view.players.map((player) => (
          <div key={player.id} className={`member-row${player.connected ? '' : ' offline'}`} style={player.team ? teamStyle(player.team) : undefined}>
            <Avatar player={player} />
            <span className="member-name">{player.name}{player.id === view.you.id && <span className="you-tag"> (you)</span>}</span>
            <span className={`role-chip${player.seat === 'spymaster' ? ' spymaster' : ''}`}>{player.team && player.seat ? `${view.teamNames[player.team]} ${player.seat}` : 'observer'}</span>
          </div>
        ))}
      </div>
      {!view.you.seat && view.phase !== 'finished' && (
        <div className="action-row">
          {(['red', 'blue'] as const).map((team) => (
            <button key={team} type="button" className="dashed-button" style={teamStyle(team)} onClick={() => act({ type: 'take-seat', team, seat: 'operative' })} data-testid={`button-join-late-${team}`}>
              + Join {view.teamNames[team]}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

function HistoryPanel({ view }: { view: RoomView }) {
  return (
    <section className="panel side-panel" aria-labelledby="history-title">
      <div className="telemetry-head">
        <span className="mono-label plain" id="history-title">Signal log</span>
        <span className="count-chip neutral" data-testid="text-clue-count">{view.clueHistory.length} {view.clueHistory.length === 1 ? 'clue' : 'clues'}</span>
      </div>
      {view.clueHistory.length ? (
        <ul className="telemetry-list" data-testid="list-clue-history">
          {[...view.clueHistory].reverse().map((record) => (
            <li key={`${record.turn}-${record.team}-${record.word}`} className="has-team" style={teamStyle(record.team)} data-testid={`row-clue-${record.turn}`}>
              <span className="telemetry-dot" />
              <div><span><b>{record.word}</b> — {numberText(record.number)}</span><time>{view.teamNames[record.team]} · turn {record.turn}</time></div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted-copy small" data-testid="empty-clue-history">No transmissions yet.</p>
      )}
    </section>
  );
}

export default App;
