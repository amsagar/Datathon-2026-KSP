import makeApiRequest from './makeApiRequest';
import { API_ENDPOINTS } from '@constants/apiEndpoints';
import type { UiLang } from '@utils/speech';
import { offlineSessionTitle } from '@utils/sessionTitleLocale';

export interface TranslateResponse {
  text: string;
  targetLang: string;
}

const memoryCache = new Map<string, string>();

const hash = (s: string): string => {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return String(h);
};

const cacheKey = (text: string, targetLang: UiLang) =>
  `${targetLang}:${text.length}:${hash(text)}`;

/**
 * Translate via the backend (therealbush/translator → Google Translate).
 * Known session titles resolve from a local dictionary first (no network).
 */
export const translateText = async (
  text: string,
  targetLang: UiLang
): Promise<string> => {
  const trimmed = (text || '').trim();
  if (!trimmed) return '';

  const dict = offlineSessionTitle(trimmed, targetLang);
  if (dict) return dict;

  const key = cacheKey(trimmed, targetLang);
  const cached = memoryCache.get(key);
  if (cached != null) return cached;

  const res = await makeApiRequest<TranslateResponse>(
    { text: trimmed, targetLang },
    API_ENDPOINTS.CHAT_TRANSLATE
  );
  const out = (res?.text || '').trim() || trimmed;
  memoryCache.set(key, out);
  return out;
};
