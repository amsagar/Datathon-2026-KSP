import { create } from 'zustand';
import { getUiLang, setUiLang, UiLang } from '@utils/speech';

/**
 * Reactive UI language. The old getUiLang() only read localStorage synchronously, so toggling
 * the language re-rendered only the component that changed it. This store makes the whole app
 * re-render on toggle, and stays the single source of truth (also persisted via speech.ts).
 */
interface LangState {
  lang: UiLang;
  setLang: (lang: UiLang) => void;
  toggle: () => void;
}

export const useLangStore = create<LangState>((set, get) => ({
  lang: getUiLang(),
  setLang: (lang) => {
    setUiLang(lang);
    set({ lang });
  },
  toggle: () => {
    const next: UiLang = get().lang === 'en' ? 'kn' : 'en';
    setUiLang(next);
    set({ lang: next });
  },
}));
