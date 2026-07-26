import type { UiLang } from '@utils/speech';

/**
 * Instant local title strings (no network). Free-form titles use backend NLLB.
 */
const OFFLINE_TITLES: Record<string, { en: string; kn: string }> = {
  'new chat': { en: 'New chat', kn: 'ಹೊಸ ಸಂಭಾಷಣೆ' },
  'greeting and introduction': {
    en: 'Greeting and Introduction',
    kn: 'ಶುಭಾಶಯ ಮತ್ತು ಪರಿಚಯ',
  },
  'ಹೊಸ ಸಂಭಾಷಣೆ': { en: 'New chat', kn: 'ಹೊಸ ಸಂಭಾಷಣೆ' },
  'ಶುಭಾಶಯ ಮತ್ತು ಪರಿಚಯ': {
    en: 'Greeting and Introduction',
    kn: 'ಶುಭಾಶಯ ಮತ್ತು ಪರಿಚಯ',
  },
};

const KANNADA_RE = /[\u0C80-\u0CFF]/;
const LATIN_RE = /[A-Za-z]/;

export const hasKannadaScript = (text: string): boolean => KANNADA_RE.test(text || '');
export const hasLatinScript = (text: string): boolean => LATIN_RE.test(text || '');

export const offlineSessionTitle = (
  title: string,
  lang: UiLang
): string | null => {
  const trimmed = (title || '').trim();
  const hit =
    OFFLINE_TITLES[trimmed.toLowerCase()] || OFFLINE_TITLES[trimmed];
  if (!hit) return null;
  return hit[lang] || hit.en;
};

export const titleNeedsTranslation = (title: string, lang: UiLang): boolean => {
  const t = (title || '').trim();
  if (!t) return false;
  if (offlineSessionTitle(t, lang)) return false;
  if (lang === 'kn') return hasLatinScript(t) && !hasKannadaScript(t);
  return hasKannadaScript(t);
};
