export type Team = 'red' | 'blue';
export type Role = Team | 'neutral' | 'assassin';
export type Phase = 'setup' | 'handoff' | 'spymaster' | 'guessing' | 'finished';
export type ClueNumber = number | 'unlimited';

export interface Card {
  id: string;
  word: string;
  role: Role;
  revealed: boolean;
}

export interface ClueRecord {
  team: Team;
  word: string;
  number: ClueNumber;
  turn: number;
}

export interface Game {
  cards: Card[];
  startingTeam: Team;
  activeTeam: Team;
  phase: Phase;
  clue: string;
  clueTeam: Team | null;
  clueNumber: ClueNumber;
  guessesRemaining: number | 'unlimited';
  teamNames: Record<Team, string>;
  clueHistory: ClueRecord[];
  winner: Team | null;
  revealKey: boolean;
  turnGuesses: number;
  penaltyRevealPending: boolean;
  resultMessage: string;
}

const WORDS = [
  'ANCHOR', 'APPLE', 'ARCH', 'ATLAS', 'BADGE', 'BAND', 'BANK', 'BARK', 'BASS',
  'BATTERY', 'BEACH', 'BELL', 'BOLT', 'BRIDGE', 'BROOM', 'BUTTON', 'CABLE',
  'CAPITAL', 'CASTLE', 'CELL', 'CHARGE', 'CHERRY', 'CIRCLE', 'CLOUD', 'COACH',
  'COMPOUND', 'CROWN', 'CRUSH', 'CURRENT', 'DATE', 'DECK', 'DIAMOND', 'DRAFT',
  'DRILL', 'DROP', 'ENGINE', 'FAN', 'FIELD', 'FIGURE', 'FILE', 'FIRE', 'FLUTE',
  'FOAM', 'FOREST', 'FRAME', 'GHOST', 'GLASS', 'GLOVE', 'GOLD', 'GRACE', 'GRASS',
  'HAMMER', 'HAWK', 'HORN', 'ICE', 'IRON', 'JACK', 'JAM', 'JUPITER', 'KANGAROO',
  'KIDNEY', 'KNIGHT', 'LAP', 'LASH', 'LEAF', 'LIMOUSINE', 'LINE', 'LINK', 'LION',
  'LOCK', 'MARCH', 'MASS', 'MERCURY', 'MILLIONAIRE', 'MINE', 'MINT', 'MODEL',
  'MOLE', 'MOON', 'MOUNT', 'MOUSE', 'NAIL', 'NEEDLE', 'NOTE', 'OCTOPUS', 'OLIVE',
  'ORANGE', 'ORGAN', 'PALM', 'PAPER', 'PARK', 'PART', 'PASS', 'PATCH', 'PILOT',
  'PITCH', 'PLANE', 'PLATE', 'POINT', 'POLE', 'POOL', 'PORT', 'PRESS', 'PUPIL',
  'RABBIT', 'RACKET', 'RAY', 'REVOLUTION', 'RING', 'ROBIN', 'ROCK', 'ROOT', 'ROSE',
  'ROW', 'SCALE', 'SCORPION', 'SCREEN', 'SCUBA DIVER', 'SEAL', 'SHADOW', 'SHARK',
  'SHEET', 'SHOE', 'SHOT', 'SINK', 'SKYSCRAPER', 'SLIP', 'SLUG', 'SMUGGLER',
  'SOUND', 'SPACE', 'SPINE', 'SPIKE', 'SPOT', 'SPRING', 'STADIUM', 'STAFF',
  'STAR', 'STATE', 'STICK', 'STRAW', 'STREAM', 'STRING', 'SUB', 'SUIT', 'SWING',
  'TABLE', 'TAG', 'TEACHER', 'TELESCOPE', 'THUMB', 'TICK', 'TIE', 'TIME', 'TRIANGLE',
  'TRIP', 'TRUNK', 'TUBE', 'WAKE', 'WALL', 'WATCH', 'WAVE', 'WEB', 'WELL', 'WHIP',
  'WIND', 'WITCH', 'WORM', 'YARD',
];

export function otherTeam(team: Team): Team {
  return team === 'red' ? 'blue' : 'red';
}

function shuffle<T>(items: T[]): T[] {
  const result = [...items];
  for (let i = result.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

export function createGame(teamNames: Record<Team, string> = { red: 'Red', blue: 'Blue' }): Game {
  const startingTeam: Team = Math.random() < 0.5 ? 'red' : 'blue';
  const roles: Role[] = [
    ...Array.from({ length: 9 }, () => startingTeam),
    ...Array.from({ length: 8 }, () => otherTeam(startingTeam)),
    ...Array.from({ length: 7 }, () => 'neutral' as const),
    'assassin',
  ];
  const words = shuffle(WORDS).slice(0, 25);
  const cards = shuffle(roles).map((role, index) => ({
    id: `card-${Date.now().toString(36)}-${index}-${Math.random().toString(36).slice(2, 7)}`,
    word: words[index],
    role,
    revealed: false,
  }));

  return {
    cards,
    startingTeam,
    activeTeam: startingTeam,
    phase: 'setup',
    clue: '',
    clueTeam: null,
    clueNumber: 1,
    guessesRemaining: 0,
    teamNames: {
      red: teamNames.red.trim() || 'Red',
      blue: teamNames.blue.trim() || 'Blue',
    },
    clueHistory: [],
    winner: null,
    revealKey: false,
    turnGuesses: 0,
    penaltyRevealPending: false,
    resultMessage: '',
  };
}

export function getRemaining(game: Game, team: Team): number {
  return game.cards.filter((card) => card.role === team && !card.revealed).length;
}

export function loadSavedGame(): Game | null {
  try {
    const saved = localStorage.getItem('word-agents-game-v1');
    if (!saved) return null;
    const parsed = JSON.parse(saved) as Game;
    if (!parsed || !Array.isArray(parsed.cards) || parsed.cards.length !== 25) return null;
    return parsed;
  } catch {
    return null;
  }
}