import { create } from 'zustand';

export type ThemePreference = 'system' | 'light' | 'dark';
export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'pods_theme';

const getSystemTheme = (): ResolvedTheme =>
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';

const resolveTheme = (preference: ThemePreference): ResolvedTheme =>
  preference === 'system' ? getSystemTheme() : preference;

const readStoredPreference = (): ThemePreference => {
  if (typeof window === 'undefined') return 'light';
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw === 'light' || raw === 'dark' || raw === 'system') return raw;
  return 'light';
};

const applyDomTheme = (resolved: ResolvedTheme) => {
  if (typeof document === 'undefined') return;
  document.documentElement.dataset.theme = resolved;
};

interface ThemeState {
  preference: ThemePreference;
  resolved: ResolvedTheme;
  initialized: boolean;
  setPreference: (preference: ThemePreference) => void;
  init: () => void;
}

let mediaQuery: MediaQueryList | null = null;
let mediaListener: ((e: MediaQueryListEvent) => void) | null = null;

const bindSystemListener = (get: () => ThemeState) => {
  if (typeof window === 'undefined') return;
  if (mediaQuery) {
    if (mediaListener) mediaQuery.removeEventListener('change', mediaListener);
    mediaQuery = null;
    mediaListener = null;
  }
  const { preference } = get();
  if (preference !== 'system') return;
  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  mediaListener = () => {
    const state = get();
    if (state.preference !== 'system') return;
    const resolved = getSystemTheme();
    applyDomTheme(resolved);
    useThemeStore.setState({ resolved });
  };
  mediaQuery.addEventListener('change', mediaListener);
};

export const useThemeStore = create<ThemeState>((set, get) => ({
  preference: 'light',
  resolved: 'light',
  initialized: false,
  setPreference: (preference) => {
    localStorage.setItem(STORAGE_KEY, preference);
    const resolved = resolveTheme(preference);
    applyDomTheme(resolved);
    set({ preference, resolved });
    bindSystemListener(get);
  },
  init: () => {
    if (get().initialized) return;
    const preference = readStoredPreference();
    const resolved = resolveTheme(preference);
    applyDomTheme(resolved);
    set({ preference, resolved, initialized: true });
    bindSystemListener(get);
  },
}));
