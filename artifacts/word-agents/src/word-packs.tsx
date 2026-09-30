import { useEffect, useState, type FormEvent } from 'react';
import { ArrowLeft, BookmarkPlus, Check, Copy, CopyPlus, Layers, Library, Link2, Pencil, Plus, Trash2, X } from 'lucide-react';
import { BOARD_SIZE, MAX_WORD_LENGTH, parseWords, type Action, type RoomView, type SavedPack } from '@workspace/game-core';
import {
  createPack,
  deletePack,
  fetchPack,
  forgetPack,
  packLink,
  rememberPack,
  updatePack,
  useBuiltInPacks,
  useMyPacks,
  usePackNames,
  type MyPack,
} from './pack-client';

type Act = (action: Action) => Promise<boolean>;
type Toast = (message: string) => void;

const MAX_CUSTOM_WORDS = 200;
const MAX_PACK_WORDS = 500;

async function copy(text: string, done: string, onToast: Toast) {
  try {
    await navigator.clipboard.writeText(text);
    onToast(done);
  } catch {
    onToast(text);
  }
}

/** Problems the server would reject, shown before anything is sent. */
function wordProblem(words: string[], min: number, max: number): string | null {
  const long = words.find((word) => word.length > MAX_WORD_LENGTH);
  if (long) return `“${long}” is too long. Keep words to ${MAX_WORD_LENGTH} characters.`;
  if (words.length > max) return `Keep it to ${max} words. This list has ${words.length}.`;
  if (words.length < min) return `Add ${min - words.length} more ${min - words.length === 1 ? 'word' : 'words'}. A pack needs at least ${min} to fill a board.`;
  return null;
}

// ---- Lobby: where the board's words come from ----

export function WordsPanel({ view, act, onToast, onManagePacks }: { view: RoomView; act: Act; onToast: Toast; onManagePacks: () => void }) {
  const { words } = view;
  const names = usePackNames(words.packs);
  const packNames = words.packs.map((id) => names[id] ?? id);

  if (!view.you.isHost) {
    return (
      <section className="panel setup-panel words-panel" aria-labelledby="words-title">
        <div className="section-heading">
          <h2 id="words-title">The word list</h2>
          {words.poolSize > 0 && <span className="role-pill">{words.poolSize} words in the pool</span>}
        </div>
        <p className="turn-copy" data-testid="text-word-sources">
          {packNames.length ? <>Dealt from <b>{packNames.join(', ')}</b></> : 'Dealt from the host’s own words'}
          {words.customCount > 0 && <>{packNames.length ? ', plus ' : ': '}<b>{words.customCount} custom {words.customCount === 1 ? 'word' : 'words'}</b> from the host</>}.
        </p>
      </section>
    );
  }
  return <HostWordsPanel view={view} act={act} onToast={onToast} onManagePacks={onManagePacks} names={names} />;
}

function HostWordsPanel({ view, act, onToast, onManagePacks, names }: { view: RoomView; act: Act; onToast: Toast; onManagePacks: () => void; names: Record<string, string> }) {
  const { words } = view;
  const catalog = useBuiltInPacks();
  const mine = useMyPacks();
  const applied = (words.customWords ?? []).join('\n');
  const [draft, setDraft] = useState(applied);
  const [code, setCode] = useState('');
  const [packName, setPackName] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => setDraft(applied), [applied]);

  const draftWords = parseWords(draft);
  const dirty = draftWords.join('\n') !== applied;
  const draftProblem = wordProblem(draftWords, 0, MAX_CUSTOM_WORDS);
  // Saved packs in play that this device doesn't have, e.g. picked by an earlier host.
  const otherPacks = words.packs.filter((id) => !catalog.some((pack) => pack.id === id) && !mine.some((pack) => pack.code === id));

  const setWords = (packs: string[], customWords = words.customWords ?? []) => act({ type: 'set-words', packs, customWords });
  const toggle = (id: string) => void setWords(words.packs.includes(id) ? words.packs.filter((item) => item !== id) : [...words.packs, id]);

  async function addByCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!code.trim()) return;
    setBusy(true);
    try {
      const pack = await fetchPack(code);
      rememberPack(pack);
      if (!words.packs.includes(pack.code) && (await setWords([...words.packs, pack.code]))) onToast(`Added “${pack.name}”.`);
      setCode('');
    } catch (error) {
      onToast((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function saveAsPack(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = wordProblem(draftWords, BOARD_SIZE, MAX_PACK_WORDS);
    if (problem) return onToast(problem);
    setBusy(true);
    try {
      const pack = await createPack(packName ?? '', draftWords);
      setPackName(null);
      onToast(`Saved “${pack.name}”. Share code ${pack.code}.`);
    } catch (error) {
      onToast((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const chip = (id: string, name: string, detail: string, description?: string) => {
    const on = words.packs.includes(id);
    return (
      <button key={id} type="button" className="pack-chip" aria-pressed={on} onClick={() => toggle(id)} data-testid={`button-pack-${id}`}>
        <span className="pack-check" aria-hidden="true">{on && <Check size={12} />}</span>
        <span className="pack-chip-text"><b>{name}</b><span>{detail}{description && <span className="pack-desc"> · {description}</span>}</span></span>
      </button>
    );
  };

  return (
    <section className="panel setup-panel words-panel" aria-labelledby="words-title">
      <div className="section-heading">
        <h2 id="words-title">The word list</h2>
        {words.poolSize > 0 && <span className="role-pill" data-testid="text-pool-size">{words.poolSize} words in the pool</span>}
      </div>

      <div className="seat-label"><Layers size={13} /> Genre packs</div>
      <div className="pack-grid">
        {catalog.map((pack) => chip(pack.id, pack.name, `${pack.size} words`, pack.description))}
      </div>

      <div className="seat-label words-subhead">
        <span><Library size={13} /> Saved packs</span>
        <button type="button" className="text-button" onClick={onManagePacks} data-testid="button-manage-packs">Manage packs</button>
      </div>
      {mine.length + otherPacks.length > 0 && (
        <div className="pack-grid">
          {mine.map((pack) => chip(pack.code, pack.name, `${pack.size} words · ${pack.code}${pack.editToken ? ' · yours' : ''}`))}
          {otherPacks.map((id) => chip(id, names[id] ?? id, id))}
        </div>
      )}
      <form className="inline-form" onSubmit={addByCode}>
        <label className="sr-only" htmlFor="pack-code">Pack code</label>
        <input id="pack-code" value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} maxLength={8} placeholder="Pack code from a friend" autoComplete="off" data-testid="input-pack-code" />
        <button type="submit" className="secondary-button" disabled={busy || !code.trim()} data-testid="button-add-pack-code"><Plus size={14} /> Add</button>
      </form>

      <div className="seat-label"><Pencil size={13} /> Your own words</div>
      <label className="sr-only" htmlFor="custom-words">Custom words, one per line or separated by commas</label>
      <textarea
        id="custom-words"
        className="words-textarea"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        placeholder={'One per line, or separated by commas.\nThese always make the board; packs fill the rest.'}
        rows={4}
        data-testid="input-custom-words"
      />
      <div className="words-foot">
        <span className={`turn-copy words-count${draftProblem ? ' is-problem' : ''}`} data-testid="text-custom-count">
          {draftProblem ?? (draftWords.length
            ? `${draftWords.length} ${draftWords.length === 1 ? 'word' : 'words'}${draftWords.length >= BOARD_SIZE ? '. They fill the whole board.' : `. Packs fill the other ${BOARD_SIZE - draftWords.length}.`}`
            : 'Office jokes, family names, anything your table will get.')}
        </span>
        <div className="action-row">
          {draftWords.length >= BOARD_SIZE && packName === null && (
            <button type="button" className="secondary-button" onClick={() => setPackName('')} data-testid="button-save-as-pack"><BookmarkPlus size={14} /> Save as pack</button>
          )}
          {dirty && (
            <button type="button" className="primary-button" disabled={Boolean(draftProblem)} onClick={() => setWords(words.packs, draftWords)} data-testid="button-apply-words">
              {draftWords.length ? 'Use these words' : 'Clear my words'}
            </button>
          )}
        </div>
      </div>
      {packName !== null && (
        <form className="inline-form" onSubmit={saveAsPack}>
          <label className="sr-only" htmlFor="new-pack-name">Pack name</label>
          <input id="new-pack-name" value={packName} onChange={(event) => setPackName(event.target.value)} maxLength={40} placeholder="Name this pack" autoFocus data-testid="input-new-pack-name" />
          <button type="submit" className="secondary-button" disabled={busy || !packName.trim()} data-testid="button-confirm-save-pack">Save</button>
          <button type="button" className="icon-button" aria-label="Cancel" onClick={() => setPackName(null)}><X size={15} /></button>
        </form>
      )}
    </section>
  );
}

// ---- The pack manager: make, edit, share and collect packs ----

type Mode = { kind: 'list' } | { kind: 'edit'; pack: MyPack | null; name: string; words: string } | { kind: 'preview'; pack: SavedPack };

export function PacksDialog({ initialCode, onClose, onToast }: { initialCode: string | null; onClose: () => void; onToast: Toast }) {
  const mine = useMyPacks();
  const [mode, setMode] = useState<Mode>({ kind: 'list' });
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState<string | null>(null);

  async function open(packCode: string) {
    setBusy(true);
    try {
      setMode({ kind: 'preview', pack: await fetchPack(packCode) });
      setCode('');
    } catch (error) {
      onToast((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (initialCode) void open(initialCode);
  }, [initialCode]);

  async function edit(pack: MyPack, asCopy: boolean) {
    setBusy(true);
    try {
      const saved = await fetchPack(pack.code);
      rememberPack(saved);
      setMode({ kind: 'edit', pack: asCopy ? null : pack, name: asCopy ? `${saved.name} (copy)`.slice(0, 40) : saved.name, words: saved.words.join('\n') });
    } catch (error) {
      onToast((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(pack: MyPack) {
    if (confirming !== pack.code) return setConfirming(pack.code);
    setConfirming(null);
    try {
      if (pack.editToken) {
        await deletePack(pack);
        onToast(`Deleted “${pack.name}”.`);
      } else {
        forgetPack(pack.code);
      }
    } catch (error) {
      onToast((error as Error).message);
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mode.kind !== 'edit') return;
    const words = parseWords(mode.words);
    const problem = wordProblem(words, BOARD_SIZE, MAX_PACK_WORDS);
    if (problem) return onToast(problem);
    setBusy(true);
    try {
      const saved = mode.pack ? await updatePack(mode.pack, mode.name, words) : await createPack(mode.name, words);
      onToast(mode.pack ? `Saved “${saved.name}”.` : `Created “${saved.name}”. Share code ${saved.code}.`);
      setMode({ kind: 'list' });
    } catch (error) {
      onToast((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const back = (
    <button type="button" className="quiet-button" onClick={() => setMode({ kind: 'list' })} data-testid="button-packs-back"><ArrowLeft size={14} /> All packs</button>
  );

  let body;
  if (mode.kind === 'edit') {
    const words = parseWords(mode.words);
    const problem = wordProblem(words, BOARD_SIZE, MAX_PACK_WORDS);
    body = (
      <form onSubmit={save}>
        <div className="field">
          <label htmlFor="pack-name">Pack name</label>
          <input id="pack-name" value={mode.name} onChange={(event) => setMode({ ...mode, name: event.target.value })} maxLength={40} placeholder="Office in-jokes" autoFocus data-testid="input-pack-name" />
        </div>
        <div className="field">
          <label htmlFor="pack-words">Words</label>
          <textarea id="pack-words" className="words-textarea tall" value={mode.words} onChange={(event) => setMode({ ...mode, words: event.target.value })} placeholder="One per line, or separated by commas." rows={9} data-testid="input-pack-words" />
        </div>
        <p className={`turn-copy words-count${problem && words.length ? ' is-problem' : ''}`}>
          {problem && words.length ? problem : `${words.length} ${words.length === 1 ? 'word' : 'words'}. Packs need ${BOARD_SIZE} to ${MAX_PACK_WORDS}.`}
        </p>
        <div className="dialog-actions">
          {back}
          <button type="submit" className="primary-button" disabled={busy || Boolean(problem) || !mode.name.trim()} data-testid="button-save-pack">
            {mode.pack ? 'Save changes' : 'Create pack'}
          </button>
        </div>
      </form>
    );
  } else if (mode.kind === 'preview') {
    const { pack } = mode;
    const saved = mine.some((item) => item.code === pack.code);
    body = (
      <>
        <div className="pack-preview-head">
          <b>{pack.name}</b>
          <span className="role-pill">{pack.code} · {pack.words.length} words</span>
        </div>
        <div className="word-cloud" data-testid="list-pack-preview">
          {pack.words.slice(0, 60).map((word) => <span key={word}>{word}</span>)}
          {pack.words.length > 60 && <span className="more">+{pack.words.length - 60} more</span>}
        </div>
        <p className="dialog-copy">{saved ? 'This pack is in your list. Pick it in any lobby you host.' : 'Save it to pick it in any lobby you host.'}</p>
        <div className="dialog-actions">
          {back}
          {!saved && (
            <button type="button" className="primary-button" onClick={() => { rememberPack(pack); onToast(`“${pack.name}” is in your packs.`); setMode({ kind: 'list' }); }} data-testid="button-save-shared-pack">
              <BookmarkPlus size={14} /> Save to my packs
            </button>
          )}
        </div>
      </>
    );
  } else {
    body = (
      <>
        <p className="dialog-copy">Packs you make or save live on this device. Share a pack’s code or link and anyone can play it.</p>
        {mine.length ? (
          <div className="pack-list" data-testid="list-my-packs">
            {mine.map((pack) => (
              <div key={pack.code} className="pack-row" data-testid={`row-pack-${pack.code}`}>
                <div className="pack-row-text">
                  <b>{pack.name}</b>
                  <span>{pack.code} · {pack.size} words · {pack.editToken ? 'made here' : 'saved'}</span>
                </div>
                <div className="pack-row-actions">
                  <button type="button" className="icon-button" aria-label={`Copy link to ${pack.name}`} onClick={() => copy(packLink(pack.code), 'Pack link copied.', onToast)}><Link2 size={15} /></button>
                  <button type="button" className="icon-button" aria-label={`Copy code for ${pack.name}`} onClick={() => copy(pack.code, `Code ${pack.code} copied.`, onToast)}><Copy size={15} /></button>
                  {pack.editToken ? (
                    <button type="button" className="icon-button" aria-label={`Edit ${pack.name}`} disabled={busy} onClick={() => edit(pack, false)} data-testid={`button-edit-pack-${pack.code}`}><Pencil size={15} /></button>
                  ) : (
                    <button type="button" className="icon-button" aria-label={`Make an editable copy of ${pack.name}`} disabled={busy} onClick={() => edit(pack, true)}><CopyPlus size={15} /></button>
                  )}
                  <button
                    type="button"
                    className={confirming === pack.code ? 'danger-button confirm-button' : 'icon-button'}
                    aria-label={pack.editToken ? `Delete ${pack.name}` : `Remove ${pack.name} from this device`}
                    onClick={() => remove(pack)}
                    onBlur={() => setConfirming(null)}
                    data-testid={`button-delete-pack-${pack.code}`}
                  >
                    {confirming === pack.code ? (pack.editToken ? 'Delete for everyone?' : 'Remove?') : <Trash2 size={15} />}
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="empty-history">No packs yet. Make one, or add a friend’s by its code.</div>
        )}
        <form className="inline-form" onSubmit={(event) => { event.preventDefault(); if (code.trim()) void open(code); }}>
          <label className="sr-only" htmlFor="shared-pack-code">Pack code</label>
          <input id="shared-pack-code" value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} maxLength={8} placeholder="Pack code from a friend" autoComplete="off" data-testid="input-shared-pack-code" />
          <button type="submit" className="secondary-button" disabled={busy || !code.trim()}>Look up</button>
        </form>
        <div className="dialog-actions">
          <button type="button" className="primary-button" onClick={() => setMode({ kind: 'edit', pack: null, name: '', words: '' })} data-testid="button-new-pack"><Plus size={14} /> New pack</button>
        </div>
      </>
    );
  }

  return (
    <div className="overlay" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="packs-title">
        <div className="dialog-head">
          <div>
            <div className="eyebrow">Your library</div>
            <h2 id="packs-title">{mode.kind === 'edit' ? (mode.pack ? 'Edit pack' : 'New pack') : mode.kind === 'preview' ? 'Shared pack' : 'Word packs'}</h2>
          </div>
          <button type="button" className="icon-button" aria-label="Close dialog" onClick={onClose} data-testid="button-close-packs"><X size={17} /></button>
        </div>
        {body}
      </section>
    </div>
  );
}
