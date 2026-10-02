import type { CardView, PlayerView, Team } from './types';

export function otherTeam(team: Team): Team {
  return team === 'red' ? 'blue' : 'red';
}

/** Mirrors GameRules.seatingProblem on the server, so the lobby can explain why it can't start yet. */
export function seatingProblem(players: Pick<PlayerView, 'team' | 'seat'>[], teamNames: Record<Team, string>): string | null {
  for (const team of ['red', 'blue'] as const) {
    const seated = players.filter((player) => player.team === team);
    if (!seated.some((player) => player.seat === 'spymaster')) return `${teamNames[team]} needs a spymaster.`;
    if (!seated.some((player) => player.seat === 'operative')) return `${teamNames[team]} needs at least one operative.`;
  }
  return null;
}

export const BOARD_SIZE = 25;
export const MAX_WORD_LENGTH = 20;

/** Splits pasted text into words: one per line, or separated by commas. Mirrors the server's clean-up. */
export function parseWords(text: string): string[] {
  const seen = new Set<string>();
  for (const raw of text.split(/[\n,;]/)) {
    const word = raw.trim().replace(/\s+/g, ' ').toUpperCase();
    if (word) seen.add(word);
  }
  return [...seen];
}

/** One word: letters or digits, apostrophes inside it (DON'T), and at most one hyphen (X-RAY). */
const ONE_WORD = /^[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*(?:-[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*)?$/u;

/** Mirrors the server's one-word check, so joined-up clues like A_B_C are caught before sending. */
export function clueFormatProblem(clue: string): string | null {
  const word = clue.trim();
  if (!word) return null;
  if (/\s/.test(word)) return 'Keep it to one word. A hyphenated word is okay.';
  if (!ONE_WORD.test(word)) return 'Keep it to one word: no symbols like _ . / or +, and at most one hyphen.';
  return null;
}

const wordParts = (text: string) => text.trim().toLowerCase().split(/[\s-]+/).filter(Boolean);

/**
 * Mirrors GameRules.boardConflict: a clue can't be an unrevealed board word,
 * a part of one, or one written without its spaces. Returns why, or null.
 */
export function clueConflict(clue: string, cards: Pick<CardView, 'word' | 'revealed'>[]): string | null {
  const parts = wordParts(clue);
  const joined = parts.join('');
  if (!joined) return null;
  for (const card of cards) {
    if (card.revealed) continue;
    const cardParts = wordParts(card.word);
    if (joined === cardParts.join('')) return `“${card.word}” is on the board. Pick a clue that isn’t one of the words.`;
    const part = parts.find((item) => cardParts.includes(item));
    if (part) return `“${part.toUpperCase()}” is part of “${card.word}” on the board. Pick a different clue.`;
  }
  return null;
}
