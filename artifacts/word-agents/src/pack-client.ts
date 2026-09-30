import { useEffect, useState } from 'react';
import type { BuiltInPack, PackGrant, SavedPack } from '@workspace/game-core';
import { RequestError, request } from './room-client';

/** A saved pack this device knows about. Only the device that made a pack holds its edit token. */
export interface MyPack {
  code: string;
  name: string;
  size: number;
  editToken?: string;
}

const STORAGE_KEY = 'word-agents-packs';
const CHANGED = 'word-agents-packs-changed';

export function packLink(code: string) {
  const base = import.meta.env.BASE_URL.replace(/\/$/, '');
  return `${window.location.origin}${base}/pack/${code}`;
}

export function loadMyPacks(): MyPack[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as MyPack[]) : [];
  } catch {
    return [];
  }
}

function saveMyPacks(packs: MyPack[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(packs));
  } catch {
    // Without storage the list lasts until this tab is closed.
  }
  memory = packs;
  window.dispatchEvent(new Event(CHANGED));
}

let memory: MyPack[] | null = null;
const current = () => memory ?? (memory = loadMyPacks());

/** Adds or refreshes a pack in this device's list, keeping an edit token it already has. */
export function rememberPack(pack: SavedPack, editToken?: string) {
  const existing = current().find((item) => item.code === pack.code);
  const entry: MyPack = { code: pack.code, name: pack.name, size: pack.words.length, editToken: editToken ?? existing?.editToken };
  saveMyPacks(existing ? current().map((item) => (item.code === pack.code ? entry : item)) : [entry, ...current()]);
}

export function forgetPack(code: string) {
  saveMyPacks(current().filter((item) => item.code !== code));
}

export function useMyPacks(): MyPack[] {
  const [packs, setPacks] = useState(current);
  useEffect(() => {
    const sync = () => setPacks(current());
    window.addEventListener(CHANGED, sync);
    return () => window.removeEventListener(CHANGED, sync);
  }, []);
  return packs;
}

let builtIns: Promise<BuiltInPack[]> | null = null;

export function fetchBuiltInPacks(): Promise<BuiltInPack[]> {
  builtIns ??= request<BuiltInPack[]>('/packs').catch((error) => {
    builtIns = null;
    throw error;
  });
  return builtIns;
}

export function useBuiltInPacks(): BuiltInPack[] {
  const [packs, setPacks] = useState<BuiltInPack[]>([]);
  useEffect(() => {
    let live = true;
    fetchBuiltInPacks().then((next) => live && setPacks(next)).catch(() => undefined);
    return () => {
      live = false;
    };
  }, []);
  return packs;
}

export function fetchPack(code: string): Promise<SavedPack> {
  return request<SavedPack>(`/packs/${encodeURIComponent(code.trim().toUpperCase())}`);
}

export async function createPack(name: string, words: string[]): Promise<SavedPack> {
  const grant = await request<PackGrant>('/packs', { method: 'POST', body: JSON.stringify({ name, words }) });
  rememberPack(grant.pack, grant.editToken);
  return grant.pack;
}

export async function updatePack(pack: MyPack, name: string, words: string[]): Promise<SavedPack> {
  const saved = await request<SavedPack>(`/packs/${pack.code}`, { method: 'PUT', body: JSON.stringify({ name, words }), token: pack.editToken });
  rememberPack(saved);
  return saved;
}

export async function deletePack(pack: MyPack): Promise<void> {
  try {
    await request(`/packs/${pack.code}`, { method: 'DELETE', token: pack.editToken });
  } catch (error) {
    // Already gone is as good as deleted.
    if (!(error instanceof RequestError && error.status === 404)) throw error;
  }
  forgetPack(pack.code);
}

const names = new Map<string, Promise<string | null>>();

/** Display names for a room's packs: built-ins and this device's packs locally, anything else from the server. */
export function usePackNames(ids: string[]): Record<string, string> {
  const catalog = useBuiltInPacks();
  const mine = useMyPacks();
  const [fetched, setFetched] = useState<Record<string, string>>({});
  const known = (id: string) => catalog.some((pack) => pack.id === id) || mine.some((pack) => pack.code === id);
  const missing = ids.filter((id) => !known(id) && !(id in fetched));

  useEffect(() => {
    if (!missing.length || !catalog.length) return undefined;
    let live = true;
    for (const id of missing) {
      if (!names.has(id)) names.set(id, fetchPack(id).then((pack) => pack.name, () => null));
      void names.get(id)!.then((name) => live && setFetched((prev) => ({ ...prev, [id]: name ?? 'Deleted pack' })));
    }
    return () => {
      live = false;
    };
  }, [missing.join(','), catalog.length]);

  const result: Record<string, string> = { ...fetched };
  for (const pack of catalog) result[pack.id] = pack.name;
  for (const pack of mine) result[pack.code] = pack.name;
  return result;
}
