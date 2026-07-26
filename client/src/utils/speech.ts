// Dictation uses browser SpeechRecognition (kn-IN / en-IN).
// Read-aloud uses backend Indian neural TTS with sentence-chunk streaming for low latency.

import { fetchTtsAudio } from '@apiCalls/tts';
import { splitSpeechChunks } from '@utils/speechChunks';

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

export type DictationEndReason = 'done' | 'error' | 'aborted';
export type DictationErrorCode = 'not-allowed' | 'no-speech' | 'network' | 'other';

export type DictationOptions = {
  /** Keep listening across pauses until the caller stops (ChatGPT-style review). */
  continuous?: boolean;
};

/**
 * Start browser dictation. Returns a stop function.
 * With {@code continuous: true}, recognition restarts on quiet gaps until stop()/abort().
 */
export function startDictation(
  lang: UiLang,
  onTranscript: (text: string, isFinal: boolean) => void,
  onEnd: (reason: DictationEndReason, errorCode?: DictationErrorCode) => void,
  options?: DictationOptions
): (() => void) | null {
  const w = window as any;
  const Ctor = w.SpeechRecognition || w.webkitSpeechRecognition;
  if (!Ctor) return null;

  const continuous = !!options?.continuous;
  let stoppedByCaller = false;
  let finalAccum = '';

  const recognition: SpeechRecognitionLike = new Ctor();
  recognition.lang = speechLocale(lang);
  recognition.continuous = continuous;
  recognition.interimResults = true;

  recognition.onresult = (event: any) => {
    let interim = '';
    let finals = '';
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const r = event.results[i];
      if (r.isFinal) finals += r[0].transcript;
      else interim += r[0].transcript;
    }
    if (finals) {
      finalAccum = continuous
        ? `${finalAccum}${finalAccum && !finalAccum.endsWith(' ') ? ' ' : ''}${finals}`.trim()
        : finals;
      onTranscript(continuous ? finalAccum : finals, true);
    } else if (interim) {
      const draft = continuous
        ? `${finalAccum}${finalAccum ? ' ' : ''}${interim}`.trim()
        : interim;
      onTranscript(draft, false);
    }
  };

  recognition.onend = () => {
    if (stoppedByCaller) {
      onEnd('done');
      return;
    }
    if (continuous) {
      // Quiet gap — keep the session alive until accept/reject.
      try {
        recognition.start();
        return;
      } catch {
        onEnd('done');
        return;
      }
    }
    onEnd('done');
  };

  recognition.onerror = (event: any) => {
    const raw = event?.error as string | undefined;
    if (raw === 'aborted' || stoppedByCaller) {
      onEnd('aborted');
      return;
    }
    // In continuous mode, ignore benign no-speech and keep listening via onend restart.
    if (continuous && (raw === 'no-speech' || raw === 'aborted')) {
      return;
    }
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
    stoppedByCaller = true;
    try {
      recognition.onend = null;
      recognition.abort();
    } catch {
      try {
        recognition.stop();
      } catch {
        // already stopped
      }
    }
  };
}

let activeAudio: HTMLAudioElement | null = null;
let speakGeneration = 0;
let paused = false;

/** Stop any in-flight backend TTS playback. */
export function stopSpeaking(): void {
  speakGeneration += 1;
  paused = false;
  if (activeAudio) {
    activeAudio.pause();
    activeAudio.src = '';
    activeAudio = null;
  }
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
  }
}

export function isSpeaking(): boolean {
  return !!(activeAudio && !activeAudio.paused && !activeAudio.ended);
}

export function isSpeakPaused(): boolean {
  return paused && !!activeAudio;
}

/** Pause current TTS chunk (keeps session so play can resume). */
export function pauseSpeaking(): void {
  if (activeAudio && !activeAudio.paused) {
    activeAudio.pause();
    paused = true;
  }
}

/** Resume a paused TTS chunk. */
export function resumeSpeaking(): void {
  if (activeAudio && activeAudio.paused && paused) {
    paused = false;
    void activeAudio.play().catch(() => undefined);
  }
}

const playBlobOnce = (blob: Blob): Promise<void> =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(blob);
    const audio = new Audio(url);
    activeAudio = audio;
    paused = false;
    const done = (err?: unknown) => {
      URL.revokeObjectURL(url);
      if (activeAudio === audio) activeAudio = null;
      paused = false;
      if (err) reject(err);
      else resolve();
    };
    audio.onended = () => done();
    audio.onerror = () => done(new Error('audio error'));
    void audio.play().catch((e) => done(e));
  });

/**
 * Speak immediately: synthesize + play the first sentence ASAP, prefetch the rest.
 * Returns a cancel function. Honors {@link pauseSpeaking} between/while chunks.
 */
export function speakIndianStreaming(
  text: string,
  lang: UiLang,
  handlers?: {
    onStart?: () => void;
    onEnd?: () => void;
    onError?: () => void;
  }
): () => void {
  const gen = ++speakGeneration;
  const chunks = splitSpeechChunks(text);
  if (!chunks.length) {
    handlers?.onEnd?.();
    return () => undefined;
  }

  let cancelled = false;
  const cancel = () => {
    cancelled = true;
    paused = false;
    speakGeneration += 1;
    if (activeAudio) {
      activeAudio.pause();
      activeAudio.src = '';
      activeAudio = null;
    }
  };

  const waitWhilePaused = async () => {
    while (paused && !cancelled && gen === speakGeneration) {
      await new Promise((r) => setTimeout(r, 80));
    }
  };

  void (async () => {
    try {
      let nextPrefetch: Promise<Blob> | null = null;
      for (let i = 0; i < chunks.length; i++) {
        if (cancelled || gen !== speakGeneration) return;
        await waitWhilePaused();
        if (cancelled || gen !== speakGeneration) return;

        const blob =
          i === 0
            ? await fetchTtsAudio(chunks[i], lang)
            : await (nextPrefetch ?? fetchTtsAudio(chunks[i], lang));
        if (cancelled || gen !== speakGeneration) return;
        if (!blob || blob.size === 0) throw new Error('empty audio');

        if (i + 1 < chunks.length) {
          nextPrefetch = fetchTtsAudio(chunks[i + 1], lang);
        } else {
          nextPrefetch = null;
        }

        if (i === 0) handlers?.onStart?.();
        await waitWhilePaused();
        if (cancelled || gen !== speakGeneration) return;
        await playBlobOnce(blob);
      }
      if (!cancelled && gen === speakGeneration) handlers?.onEnd?.();
    } catch {
      if (!cancelled && gen === speakGeneration) handlers?.onError?.();
    }
  })();

  return cancel;
}

/** Play a single MP3 blob. Prefer {@link speakIndianStreaming} for chat replies. */
export function playAudioBlob(
  blob: Blob,
  onEnded?: () => void
): () => void {
  stopSpeaking();
  const url = URL.createObjectURL(blob);
  const audio = new Audio(url);
  activeAudio = audio;
  const cleanup = () => {
    URL.revokeObjectURL(url);
    if (activeAudio === audio) activeAudio = null;
    onEnded?.();
  };
  audio.onended = cleanup;
  audio.onerror = cleanup;
  void audio.play().catch(() => cleanup());
  return () => {
    audio.onended = null;
    audio.onerror = null;
    audio.pause();
    URL.revokeObjectURL(url);
    if (activeAudio === audio) activeAudio = null;
  };
}
