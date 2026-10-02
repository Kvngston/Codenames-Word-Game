export type Team = 'red' | 'blue';
export type CardRole = Team | 'neutral' | 'assassin';
export type Seat = 'spymaster' | 'operative';
export type Phase = 'lobby' | 'clue' | 'guessing' | 'finished';
export type ClueNumber = number | 'unlimited';
export type GuessBudget = number | 'unlimited';

// The client contract for the Spring Boot API (server/). These mirror
// RoomViews and GameAction in com.tk.wordagents; keep them in sync.

export interface ClueRecord {
  team: Team;
  word: string;
  number: ClueNumber;
  turn: number;
}

export interface ActiveClue {
  team: Team;
  word: string;
  number: ClueNumber;
}

/** The host's turn timer. Off by default; the times apply once it's on. */
export interface TimerSettings {
  on: boolean;
  /** How long a spymaster has for a clue, 30 to 600. */
  spymasterSeconds: number;
  /** How long the operatives have to guess, 30 to 600. */
  operativeSeconds: number;
}

/** Where a room's board words come from. */
export interface WordSettings {
  /** Built-in pack ids (e.g. "movies") and saved-pack codes. */
  packs: string[];
  /** The host's own words; null for everyone else, so they stay a surprise. */
  customWords: string[] | null;
  customCount: number;
  /** Distinct words the last deal drew from. */
  poolSize: number;
}

// ---- What a single player's device receives. ----

export interface CardView {
  id: string;
  word: string;
  revealed: boolean;
  /** null means the viewer is not allowed to know this card's role. */
  role: CardRole | null;
  /** Ids of the players who highlighted this card this turn. Everyone sees them. */
  highlightedBy: string[];
}

export interface PlayerView {
  id: string;
  name: string;
  team: Team | null;
  seat: Seat | null;
  connected: boolean;
  isHost: boolean;
}

export interface RoomView {
  code: string;
  version: number;
  you: PlayerView;
  players: PlayerView[];
  teamNames: Record<Team, string>;
  phase: Phase;
  startingTeam: Team;
  activeTeam: Team;
  cards: CardView[];
  remaining: Record<Team, number>;
  totals: Record<Team, number>;
  clue: ActiveClue | null;
  guessesRemaining: GuessBudget;
  turnGuesses: number;
  clueHistory: ClueRecord[];
  winner: Team | null;
  resultMessage: string;
  penaltyRevealPending: boolean;
  /** True for everyone while a clue is being reviewed. */
  reviewPending: boolean;
  /** The clue under review; only sent to spymasters. */
  review: ActiveClue | null;
  words: WordSettings;
  lastEvent: string;
  timer: TimerSettings;
  /**
   * Epoch millis when the turn's time runs out, or null outside a turn or
   * with the timer off. useRoom shifts it onto this device's clock.
   */
  turnEndsAt: number | null;
  /** The server's clock when the view was built. */
  serverTime: number;
}

export type Action =
  | { type: 'take-seat'; team: Team; seat: Seat }
  | { type: 'leave-seat' }
  | { type: 'rename-team'; team: Team; name: string }
  | { type: 'set-words'; packs: string[]; customWords: string[] }
  | { type: 'set-timer'; on: boolean; spymasterSeconds?: number; operativeSeconds?: number }
  | { type: 'start' }
  | { type: 'give-clue'; word: string; number: ClueNumber }
  | { type: 'review-clue'; uphold: boolean }
  | { type: 'penalty-reveal'; cardId: string }
  | { type: 'skip-penalty' }
  | { type: 'guess'; cardId: string }
  | { type: 'guesses'; cardIds: string[] }
  | { type: 'set-highlights'; cardIds: string[] }
  | { type: 'end-turn' }
  | { type: 'new-game' };

// ---- Word packs (/api/packs). Mirrors PackService. ----

export interface BuiltInPack {
  id: string;
  name: string;
  description: string;
  size: number;
  sample: string[];
}

export interface SavedPack {
  code: string;
  name: string;
  words: string[];
}

export interface PackGrant {
  code: string;
  editToken: string;
  pack: SavedPack;
}
