import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'ca:sidebarWidth';

export const SIDEBAR_DEFAULT_WIDTH = 260;
export const SIDEBAR_MIN_WIDTH = 220;
export const SIDEBAR_MAX_WIDTH = 480;
export const SIDEBAR_COLLAPSED_WIDTH = 56;

const clamp = (n: number) =>
  Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, n));

const readInitial = (): number => {
  if (typeof window === 'undefined') return SIDEBAR_DEFAULT_WIDTH;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return SIDEBAR_DEFAULT_WIDTH;
    const n = Number.parseInt(raw, 10);
    if (Number.isNaN(n)) return SIDEBAR_DEFAULT_WIDTH;
    return clamp(n);
  } catch {
    return SIDEBAR_DEFAULT_WIDTH;
  }
};

/**
 * Width of the chat sidebar in pixels, persisted to localStorage. Exposes a
 * `beginResize` helper that callers attach to the resizer's `onMouseDown`;
 * subsequent mouse-move / mouse-up listeners are attached and torn down
 * internally.
 */
export const useSidebarWidth = () => {
  const [width, setWidth] = useState<number>(readInitial);
  const [resizing, setResizing] = useState(false);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, String(width));
    } catch {
      // ignore quota / privacy-mode failures
    }
  }, [width]);

  useEffect(() => {
    if (!resizing) return;
    const prevCursor = document.body.style.cursor;
    const prevUserSelect = document.body.style.userSelect;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const onMove = (e: MouseEvent) => {
      setWidth(clamp(e.clientX));
    };
    const onUp = () => setResizing(false);

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      document.body.style.cursor = prevCursor;
      document.body.style.userSelect = prevUserSelect;
    };
  }, [resizing]);

  const beginResize = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setResizing(true);
  }, []);

  return { width, resizing, beginResize };
};
