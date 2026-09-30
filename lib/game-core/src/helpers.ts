import type { PlayerView, Team } from './types';

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
