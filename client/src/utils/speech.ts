// Voice + language helpers built on the browser Web Speech API (no backend needed).
// Language preference drives speech recognition (kn-IN / en-IN) and the localized composer
// strings. Read-aloud voice selection instead detects the actual answer text's script — the UI
// toggle and the assistant's actual reply language can disagree (e.g. UI left in English while
// the user's chat turned to Kannada), and reading English text with a kn-IN voice (or vice versa)
// sounds wrong even though the toggle "matches".

const LANG_KEY = 'crime_ai_lang';

export type UiLang = 'en' | 'kn';

export function getUiLang(): UiLang {
  return localStorage.getItem(LANG_KEY) === 'kn' ? 'kn' : 'en';
}

export function setUiLang(lang: UiLang): void {
  localStorage.setItem(LANG_KEY, lang);
}

export function speechLocale(lang: UiLang): string {
  return lang === 'kn' ? 'kn-IN' : 'en-IN';
}

// Kannada Unicode block (U+0C80–U+0CFF), same range already used for filename sanitization in
// exportChatPdf.ts. A single Kannada character is enough signal — mixed script answers (a Kannada
// reply citing an English proper noun) should still read with the Kannada voice.
const KANNADA_RANGE = /[ಀ-೿]/;

/** Detects the script actually present in `text`, independent of the UI language toggle. */
export function detectSpeechLang(text: string): UiLang {
  return KANNADA_RANGE.test(text) ? 'kn' : 'en';
}

type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((event: any) => void) | null;
  onend: (() => void) | null;
  onerror: ((event: any) => void) | null;
};

export function isSpeechRecognitionSupported(): boolean {
  const w = window as any;
  return !!(w.SpeechRecognition || w.webkitSpeechRecognition);
}

/** Reason a dictation session ended, so callers can tell a denied mic apart from a normal stop. */
export type DictationEndReason = 'done' | 'error';

/** Coarse error category — callers map this to a localized message; kept code-only here since
 * this module has no access to the UI translation dictionary. */
export type DictationErrorCode = 'not-allowed' | 'no-speech' | 'network' | 'other';

/**
 * Starts dictation in the given language. Returns a stop function, or null when the
 * browser has no SpeechRecognition (e.g. Firefox) — callers should hide the mic then.
 *
 * `onEnd` used to also be wired directly to `recognition.onerror`, so a denied mic permission
 * (`not-allowed`), a dropped network connection, or "no speech detected" were all silently
 * indistinguishable from the user just stopping normally. It now receives a reason and, on error,
 * a coarse error code for the caller to localize and display.
 */
export function startDictation(
  lang: UiLang,
  onTranscript: (text: string, isFinal: boolean) => void,
  onEnd: (reason: DictationEndReason, errorCode?: DictationErrorCode) => void
): (() => void) | null {
  const w = window as any;
  const Ctor = w.SpeechRecognition || w.webkitSpeechRecognition;
  if (!Ctor) return null;
  const recognition: SpeechRecognitionLike = new Ctor();
  recognition.lang = speechLocale(lang);
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.onresult = (event: any) => {
    let interim = '';
    let final = '';
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const r = event.results[i];
      if (r.isFinal) final += r[0].transcript;
      else interim += r[0].transcript;
    }
    if (final) onTranscript(final, true);
    else if (interim) onTranscript(interim, false);
  };
  recognition.onend = () => onEnd('done');
  recognition.onerror = (event: any) => {
    const raw = event?.error as string | undefined;
    const errorCode: DictationErrorCode =
      raw === 'not-allowed' || raw === 'permission-denied'
        ? 'not-allowed'
        : raw === 'no-speech'
          ? 'no-speech'
          : raw === 'network'
            ? 'network'
            : 'other';
    onEnd('error', errorCode);
  };
  try {
    recognition.start();
  } catch {
    return null;
  }
  return () => {
    try {
      recognition.stop();
    } catch {
      // already stopped
    }
  };
}

// Voices load asynchronously in Chrome — getVoices() commonly returns [] on the very first call
// after page load, so the first read-aloud of a session silently got no matching voice (kn-IN
// stayed silent since no voice was ever assigned). Warm the list eagerly and keep it fresh via
// voiceschanged, instead of only ever calling getVoices() lazily inside speak().
let cachedVoices: SpeechSynthesisVoice[] = [];
if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
  const refresh = () => {
    cachedVoices = window.speechSynthesis.getVoices();
  };
  refresh();
  window.speechSynthesis.onvoiceschanged = refresh;
}

/** Reads text aloud, preferring a voice that matches the language. Returns a cancel fn. */
export function speak(text: string, lang: UiLang): () => void {
  if (!('speechSynthesis' in window) || !text) return () => undefined;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  const locale = speechLocale(lang);
  const voices = cachedVoices.length ? cachedVoices : window.speechSynthesis.getVoices();
  const voice =
    voices.find((v) => v.lang === locale) ||
    voices.find((v) => v.lang.startsWith(locale.slice(0, 2)));
  if (voice) utterance.voice = voice;
  utterance.lang = locale;
  window.speechSynthesis.speak(utterance);
  return () => window.speechSynthesis.cancel();
}

export function isSpeaking(): boolean {
  return 'speechSynthesis' in window && window.speechSynthesis.speaking;
}

export function stopSpeaking(): void {
  if ('speechSynthesis' in window) window.speechSynthesis.cancel();
}
