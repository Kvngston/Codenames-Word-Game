import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent, type PointerEvent } from 'react';
import { Hand, Highlighter, Pointer, Undo2, X } from 'lucide-react';

/** How long a word must be held to guess it. */
export const HOLD_MS = 800;
/** After a hold, the guess waits this long so it can be undone. */
export const UNDO_SECONDS = 3;
/** A press longer than this isn't a tap, so it never toggles a highlight. */
const TAP_MS = 250;
/** Moving further than this (a scroll) cancels the hold. */
const MOVE_PX = 10;

interface Press {
  id: string;
  x: number;
  y: number;
  started: number;
  timer: number;
  done: boolean;
}

/**
 * Tap versus press-and-hold on board tiles. A tap calls onTap; holding for
 * HOLD_MS calls onHold. Letting go early, or moving the finger, cancels the
 * hold, and a cancelled hold never counts as a tap either.
 */
export function useTapOrHold({ onTap, onHold, onShortHold }: { onTap: (id: string) => void; onHold: (id: string) => void; onShortHold: () => void }) {
  const [holding, setHolding] = useState<string | null>(null);
  const press = useRef<Press | null>(null);

  function stop() {
    if (press.current) window.clearTimeout(press.current.timer);
    press.current = null;
    setHolding(null);
  }

  function bind(id: string) {
    return {
      onPointerDown(event: PointerEvent<HTMLElement>) {
        if (event.button !== 0) return;
        stop();
        const timer = window.setTimeout(() => {
          if (!press.current || press.current.id !== id) return;
          press.current.done = true;
          setHolding(null);
          navigator.vibrate?.(40);
          onHold(id);
        }, HOLD_MS);
        press.current = { id, x: event.clientX, y: event.clientY, started: performance.now(), timer, done: false };
        setHolding(id);
      },
      onPointerMove(event: PointerEvent<HTMLElement>) {
        const current = press.current;
        if (current && !current.done && Math.hypot(event.clientX - current.x, event.clientY - current.y) > MOVE_PX) stop();
      },
      onPointerUp() {
        const current = press.current;
        if (!current || current.id !== id) return;
        const held = performance.now() - current.started;
        stop();
        if (current.done) return;
        if (held < TAP_MS) onTap(id);
        else onShortHold();
      },
      onPointerLeave: stop,
      onPointerCancel: stop,
      // A long press opens the browser's context menu on Android and the callout on iOS.
      onContextMenu: (event: MouseEvent<HTMLElement>) => event.preventDefault(),
      // Keyboard users: Enter or Space highlights. Pointer taps are handled above.
      onKeyDown(event: KeyboardEvent<HTMLElement>) {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onTap(id);
        }
      },
    };
  }

  return { holding, bind };
}

const TIP_KEYS = { guess: 'word-agents-tip-guess', watch: 'word-agents-tip-watch' } as const;
type Tip = keyof typeof TIP_KEYS;

function seen(tip: Tip): boolean {
  try {
    return localStorage.getItem(TIP_KEYS[tip]) === '1';
  } catch {
    return false;
  }
}

function markSeen(tip: Tip) {
  try {
    localStorage.setItem(TIP_KEYS[tip], '1');
  } catch {
    // Without storage the tip just shows again next time.
  }
}

/** Brings the gesture tips back, e.g. from the rules dialog. */
export function resetGestureTips() {
  try {
    Object.values(TIP_KEYS).forEach((key) => localStorage.removeItem(key));
  } catch {
    // Nothing stored to clear.
  }
  window.dispatchEvent(new Event('word-agents-tips-reset'));
}

/**
 * Shown once per device: to the guessing operative the first time it's their
 * turn, and to everyone else the first time highlights appear on the board.
 */
export function GestureTips({ guessing, highlightsVisible }: { guessing: boolean; highlightsVisible: boolean }) {
  const [, setVersion] = useState(0);
  const tip: Tip | null = guessing ? (seen('guess') ? null : 'guess') : highlightsVisible && !seen('watch') ? 'watch' : null;

  // Re-render when the rules dialog resets the tips.
  useEffect(() => {
    const onReset = () => setVersion((v) => v + 1);
    window.addEventListener('word-agents-tips-reset', onReset);
    return () => window.removeEventListener('word-agents-tips-reset', onReset);
  }, []);

  if (!tip) return null;
  const dismiss = () => {
    markSeen(tip);
    setVersion((v) => v + 1);
  };

  return (
    <section className="gesture-tips" role="note" aria-label="How highlighting and guessing work" data-testid={`panel-tip-${tip}`}>
      <button type="button" className="icon-button tip-close" aria-label="Dismiss tip" onClick={dismiss}><X size={14} /></button>
      {tip === 'guess' ? (
        <>
          <span className="mono-label gold">How to guess</span>
          <ul className="tip-list">
            <li><span className="tip-icon"><Pointer size={16} /></span><div><b>Tap a word to highlight it</b><span>Everyone sees your highlights, so use them to share your thinking. Tap again to remove it.</span></div></li>
            <li><span className="tip-icon"><Hand size={16} /></span><div><b>Press and hold to guess</b><span>Keep holding until the bar fills. Let go early, or slide your finger away, to cancel.</span></div></li>
            <li><span className="tip-icon"><Undo2 size={16} /></span><div><b>{UNDO_SECONDS} seconds to undo</b><span>Every guess waits {UNDO_SECONDS} seconds before it’s sent. Tap Undo if you change your mind.</span></div></li>
          </ul>
          <button type="button" className="gold-button compact" onClick={dismiss} data-testid="button-dismiss-tip">Got it</button>
        </>
      ) : (
        <div className="tip-inline">
          <span className="tip-icon"><Highlighter size={16} /></span>
          <p className="muted-copy small">
            <b>Highlights</b> show which words the operatives are considering, with the initials of who picked them. They aren’t guesses: a word is only guessed when an operative presses and holds it.
          </p>
          <button type="button" className="ghost-button compact" onClick={dismiss} data-testid="button-dismiss-tip">Got it</button>
        </div>
      )}
    </section>
  );
}
