import { useCallback, useEffect, useRef, useState } from 'react';
import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import type { Action, RoomView } from '@workspace/game-core';

// The Spring Boot API runs on its own host in production (VITE_API_URL, e.g.
// https://api.example.com). Unset, it's same-origin: Vite proxies /api and /ws in dev.
const API_ORIGIN = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
const API = `${API_ORIGIN}/api`;
const VIEW_DESTINATION = '/user/topic/view';

function socketUrl() {
  return `${API_ORIGIN || window.location.origin}/ws`.replace(/^http/, 'ws');
}

export interface Seat {
  code: string;
  playerId: string;
  token: string;
}

export type Connection = 'connecting' | 'live' | 'reconnecting' | 'lost';

const seatKey = (code: string) => `word-agents-seat:${code.toUpperCase()}`;

// Only the seat token is kept on the device. The game itself always comes from the server.
export function loadSeat(code: string): Seat | null {
  try {
    const saved = localStorage.getItem(seatKey(code));
    return saved ? (JSON.parse(saved) as Seat) : null;
  } catch {
    return null;
  }
}

function saveSeat(seat: Seat) {
  try {
    localStorage.setItem(seatKey(seat.code), JSON.stringify(seat));
  } catch {
    // Without storage the seat still works until this tab is closed.
  }
}

export function forgetSeat(code: string) {
  try {
    localStorage.removeItem(seatKey(code));
  } catch {
    // Nothing to clear.
  }
}

export class RequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function request<T>(path: string, init: RequestInit & { token?: string } = {}): Promise<T> {
  const { token, ...rest } = init;
  const response = await fetch(`${API}${path}`, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null;
    throw new RequestError(response.status, body?.error ?? 'The table could not be reached. Try again.');
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}

export async function createRoom(name: string): Promise<Seat> {
  const seat = await request<Seat>('/rooms', { method: 'POST', body: JSON.stringify({ name }) });
  saveSeat(seat);
  return seat;
}

export async function joinRoom(code: string, name: string): Promise<Seat> {
  const seat = await request<Seat>(`/rooms/${encodeURIComponent(code.toUpperCase())}/players`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
  saveSeat(seat);
  return seat;
}

export function sendAction(seat: Seat, action: Action): Promise<RoomView> {
  return request(`/rooms/${seat.code}/actions`, { method: 'POST', body: JSON.stringify(action), token: seat.token });
}

export async function leaveRoom(seat: Seat): Promise<void> {
  await request(`/rooms/${seat.code}/players/me`, { method: 'DELETE', token: seat.token });
  forgetSeat(seat.code);
}

/**
 * Keeps this seat's private view of the room up to date. The view is loaded
 * over HTTP, then the server pushes a fresh copy over STOMP after every change.
 */
export function useRoom(seat: Seat | null) {
  const [view, setView] = useState<RoomView | null>(null);
  const [connection, setConnection] = useState<Connection>('connecting');
  const latest = useRef<RoomView | null>(null);

  const accept = useCallback((next: RoomView) => {
    const current = latest.current;
    if (current && current.version > next.version) return;
    if (current && JSON.stringify(current) === JSON.stringify(next)) return;
    latest.current = next;
    setView(next);
  }, []);

  useEffect(() => {
    if (!seat) return undefined;
    latest.current = null;
    setView(null);
    setConnection('connecting');
    let lost = false;
    let retries = 0;

    const client = new Client({
      brokerURL: socketUrl(),
      connectHeaders: { room: seat.code, token: seat.token },
      // 1 s, 2 s, 4 s … up to 30 s between attempts, plus a random wait below,
      // so a server restart doesn't bring every phone back in the same second.
      reconnectDelay: 1000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: 30_000,
      beforeConnect: async () => {
        if (retries > 0) await new Promise((resolve) => setTimeout(resolve, Math.random() * Math.min(1000 * 2 ** retries, 10_000)));
        retries += 1;
      },
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        retries = 0;
        client.subscribe(VIEW_DESTINATION, (message) => accept(JSON.parse(message.body) as RoomView));
        setConnection('live');
        // Catch anything that changed while we were disconnected.
        void load();
      },
      // The server refuses a CONNECT for a missing room or a bad seat; HTTP says which.
      onStompError: () => void load(),
      onWebSocketClose: () => {
        if (!lost) setConnection('reconnecting');
      },
    });

    async function load() {
      try {
        accept(await request<RoomView>(`/rooms/${seat!.code}/view`, { token: seat!.token }));
      } catch (error) {
        if (error instanceof RequestError && (error.status === 403 || error.status === 404)) {
          lost = true;
          setConnection('lost');
          void client.deactivate();
        }
      }
    }

    void load();
    client.activate();
    return () => {
      lost = true;
      void client.deactivate();
    };
  }, [seat?.code, seat?.token, accept]);

  return { view, connection, accept };
}
